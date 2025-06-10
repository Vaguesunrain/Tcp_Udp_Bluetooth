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

// --- 新的UI State ---
data class BleUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isServer: Boolean = false, // 新增：标记是否为服务器(外围设备)模式
    val connectedDeviceName: String? = null,
    val messages: List<String> = emptyList()
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
        // 在ViewModel初始化时创建Manager
        bleChatManager = BleChatManager(application, viewModelScope, ::handleManagerState)
    }

    // UI调用此方法启动服务器(外围设备)模式
    fun startServerMode() {
        _uiState.update { it.copy(isServer = true) }
        bleChatManager?.startServer()
    }

    // UI调用此方法启动客户端(中心设备)扫描
    fun startClientMode() {
        _uiState.update { it.copy(isServer = false) }
        bleChatManager?.startScan()
    }

    // UI调用此方法连接到扫描到的设备
    fun connectToDevice(device: BluetoothDevice) {
        if (uiState.value.isConnecting || uiState.value.isConnected) return
        bleChatManager?.connectToDevice(device)
    }

    fun disconnect() {
        bleChatManager?.disconnect()
    }

    fun sendMessage(message: String) {
        bleChatManager?.sendMessage(message)
        // 立即在UI上显示自己发送的消息
        val sentMessage = "Me:$message"
        _uiState.update { it.copy(messages = it.messages + sentMessage) }
    }

    private fun handleManagerState(state: BleChatManager.ManagerState) {
        viewModelScope.launch {
            when (state) {
                is BleChatManager.ManagerState.Connecting -> {
                    _uiState.update { it.copy(isConnecting = true, isConnected = false, connectedDeviceName = state.device.name) }
                }
                is BleChatManager.ManagerState.Connected -> {
                    _uiState.update { it.copy(isConnecting = false, isConnected = true, connectedDeviceName = state.device.name) }
                    Toast.makeText(getApplication(), "Connected to ${state.device.name}", Toast.LENGTH_SHORT).show()
                }
                is BleChatManager.ManagerState.Disconnected -> {
                    _uiState.update { it.copy(isConnecting = false, isConnected = false, connectedDeviceName = null, messages = emptyList()) }
                    Toast.makeText(getApplication(), "Disconnected", Toast.LENGTH_SHORT).show()
                }
                is BleChatManager.ManagerState.MessageReceived -> {
                    val fullMessage = "${uiState.value.connectedDeviceName ?: "Them"}:${state.message}"
                    _uiState.update { it.copy(messages = it.messages + fullMessage) }
                }
                is BleChatManager.ManagerState.ScanStarted -> {
                    _uiState.update { it.copy(isScanning = true, scannedDevices = emptyList()) }
                }
                is BleChatManager.ManagerState.ScanStopped -> {
                    _uiState.update { it.copy(isScanning = false) }
                }
                is BleChatManager.ManagerState.DeviceFound -> {
                    // 避免重复添加
                    if (state.device.address !in _uiState.value.scannedDevices.map { it.address }) {
                        _uiState.update { it.copy(scannedDevices = it.scannedDevices + state.device) }
                    }
                }
                is BleChatManager.ManagerState.Error -> {
                    Log.e(TAG, "BLE Error: ${state.message}")
                    Toast.makeText(getApplication(), "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleChatManager?.close()
        INSTANCE = null // 清理单例
    }
}