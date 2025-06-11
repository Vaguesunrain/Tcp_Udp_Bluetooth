package com.ame.tcp_udp.BlueTooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BleViewModel"
data class BleUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isServer: Boolean = false,
    val connectedDevice: BluetoothDevice? = null,
    val connectingDevice: BluetoothDevice? = null,
    val messages: List<String> = emptyList(),
    val connectedDeviceName: String? = null
)

@SuppressLint("MissingPermission")
class BleViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        private var INSTANCE: BleViewModel? = null
        fun getInstance(application: Application): BleViewModel {
            return INSTANCE ?: synchronized(this) {
                BleViewModel(application).also { INSTANCE = it }
            }
        }
    }

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    private var bleChatManager: BleChatManager? = null

    init {
        bleChatManager = BleChatManager(application, viewModelScope, ::handleManagerState)
    }

    fun startServerMode() {
        // 重置状态以确保干净的服务器启动
        _uiState.update { it.copy(
            isServer = true,
            isConnected = false,
            isConnecting = false,
            connectedDevice = null,
            connectingDevice =null,
            connectedDeviceName = null,
            messages = emptyList()
        ) }
        bleChatManager?.startServer()
    }

    fun startClientMode() {
        _uiState.update { it.copy(isServer = false, scannedDevices = emptyList()) }
        bleChatManager?.startScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (uiState.value.isConnecting || uiState.value.isConnected) return

        // 在发起连接时，立即更新UI状态，包括设备名称
        _uiState.update { it.copy(
            isConnecting = true,
            connectingDevice = device,
            connectedDeviceName = device.name ?: "Unknown Device" // 立即设置名称
        ) }
        bleChatManager?.connectToDevice(device)
    }

    fun disconnect() {
        // If this device is the server, send a disconnect command to the client first.
        if (_uiState.value.isServer && _uiState.value.isConnected) {
            Log.d(TAG, "Server initiating disconnect. Sending command to client.")
            // This sends the message over BLE but does NOT add it to the local UI chat list.
            bleChatManager?.sendMessage(BleChatManager.DISCONNECT_MESSAGE)
        }
        // Proceed with the actual disconnection for both client and server.
        bleChatManager?.disconnect()
    }

    fun stopScan() {
        bleChatManager?.stopScan()
    }

    fun sendMessage(message: String) {
        bleChatManager?.sendMessage(message)
        val sentMessage = "Me:$message"
        _uiState.update { it.copy(messages = it.messages + sentMessage) }
    }
    fun clearScannedDevices() {
        _uiState.update { it.copy(scannedDevices = emptyList()) }
    }
    private fun handleManagerState(state: BleChatManager.ManagerState) {
        viewModelScope.launch {
            when (state) {
                is BleChatManager.ManagerState.Connecting -> {
                    // 这个状态由 connectToDevice 立即处理，这里可以忽略
                }
                is BleChatManager.ManagerState.Connected -> {
                    _uiState.update { it.copy(
                        isConnecting = false,
                        isConnected = true,
                        connectedDevice = state.device,
                        connectingDevice = null,
                        // 再次更新名称，以防广播名和连接后的名称不一致
                        connectedDeviceName = state.device.name ?: "Unknown Device"
                    )}
                    Toast.makeText(getApplication(), "Connected to ${state.device.name}", Toast.LENGTH_SHORT).show()
                }
                is BleChatManager.ManagerState.Disconnected -> {
                    // 保留 connectedDeviceName 以便UI可以显示 "与 '设备X' 的连接已断开"
                    _uiState.update { it.copy(
                        isConnecting = false,
                        isConnected = false,
                        connectedDevice = null,
                        messages = emptyList()
                        // 注意：此处特意不清除 connectedDeviceName
                    )}
                    // 使用已保存的设备名来显示 Toast
                    val deviceName = uiState.value.connectedDeviceName ?: "device"
                    Toast.makeText(getApplication(), "Disconnected from $deviceName", Toast.LENGTH_SHORT).show()
                }
                is BleChatManager.ManagerState.MessageReceived -> {
                    // 使用已保存的 connectedDeviceName 作为备用名称
                    val senderName = uiState.value.connectedDevice?.name ?: uiState.value.connectedDeviceName ?: "Them"
                    val fullMessage = "$senderName:${state.message}"
                    _uiState.update { it.copy(messages = it.messages + fullMessage) }
                }
                is BleChatManager.ManagerState.ScanStarted -> {
                    _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList()) }
                }
                is BleChatManager.ManagerState.ScanStopped -> {
                    _uiState.update { it.copy(isScanning = false) }
                }
                is BleChatManager.ManagerState.DeviceFound -> {
                    if (state.device.address !in _uiState.value.scannedDevices.map { it.address }) {
                        _uiState.update { it.copy(scannedDevices = it.scannedDevices + state.device) }
                    }
                }
                is BleChatManager.ManagerState.Error -> {
                    _uiState.update { it.copy(isConnecting = false, connectedDevice = null) }
                    val deviceName = uiState.value.connectedDeviceName ?: "the device"
                    Log.e(TAG, "BLE Error with $deviceName: ${state.message}")
                    Toast.makeText(getApplication(), "Error with $deviceName: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        bleChatManager?.close()
    }
}