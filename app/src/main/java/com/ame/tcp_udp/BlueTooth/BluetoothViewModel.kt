package com.ame.tcp_udp.BlueTooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val TAG = "BluetoothService"
private const val SERVICE_NAME = "BluetoothChatApp"
// A unique UUID for this application's Bluetooth service.
private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID

// --- Data class for UI State ---
data class BluetoothUiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectedDeviceName: String? = null,
    val messages: List<String> = emptyList()
)

// --- ViewModel ---
@SuppressLint("MissingPermission")
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    // 单例模式的 companion object 保持不变
    companion object {
        @Volatile
        private var INSTANCE: BluetoothViewModel? = null

        fun getInstance(application: Application): BluetoothViewModel {
            return INSTANCE ?: synchronized(this) {
                val instance = BluetoothViewModel(application)
                INSTANCE = instance
                instance
            }
        }
    }

    private val bluetoothManager = application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()
    private var bluetoothService: BluetoothService? = null

    init {
        // *** 移除所有自动启动逻辑 ***
        // 创建一个服务实例，但什么也不做
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothService = BluetoothService(bluetoothAdapter) { state, device, msg ->
                viewModelScope.launch { handleConnectionState(state, device, msg) }
            }
        }
    }

    // UI调用这个方法来明确地启动服务器
    fun startServer() {
        bluetoothService?.start()
    }

    // UI调用这个方法来明确地作为客户端连接
    fun connectToDevice(device: BluetoothDevice) {
        if (uiState.value.isConnecting || uiState.value.isConnected) return
        bluetoothService?.connect(device)
    }

    // UI调用这个方法来断开连接并停止所有活动
    fun disconnect() {
        bluetoothService?.stop()
    }

    // handleConnectionState, updatePairedDevices, sendMessage 等方法保持不变
    // ...
    private fun handleConnectionState(state: ConnectionState, device: BluetoothDevice?, message: String?) {
        when (state) {
            ConnectionState.CONNECTING -> {
                _uiState.update { it.copy(isConnecting = true, isConnected = false, connectedDeviceName = device?.name) }
            }
            ConnectionState.CONNECTED -> {
                _uiState.update {
                    it.copy(isConnecting = false, isConnected = true, connectedDeviceName = device?.name)
                }
                Toast.makeText(getApplication(), "Connected to ${device?.name}", Toast.LENGTH_SHORT).show()
            }
            ConnectionState.DISCONNECTED -> {
                _uiState.update {
                    it.copy(isConnecting = false, isConnected = false, connectedDeviceName = null, messages = emptyList())
                }
                Toast.makeText(getApplication(), "Disconnected", Toast.LENGTH_SHORT).show()
            }
            ConnectionState.MESSAGE_RECEIVED -> {
                message?.let { msg ->
                    val fullMessage = "${uiState.value.connectedDeviceName ?: "Them"}:$msg"
                    _uiState.update { it.copy(messages = it.messages + fullMessage) }
                }
            }
        }
    }

    fun updatePairedDevices() {
        if (bluetoothAdapter?.isEnabled == true) {
            _uiState.update { it.copy(pairedDevices = bluetoothAdapter.bondedDevices.toList()) }
        } else {
            _uiState.update { it.copy(pairedDevices = emptyList()) }
        }
    }

    fun sendMessage(message: String) {
        val sentMessage = "Me:$message"
        _uiState.update { it.copy(messages = it.messages + sentMessage) }
        bluetoothService?.write(message.toByteArray())
    }

    fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == false) {
            _uiState.update { it.copy(discoveredDevices = emptyList()) }
            bluetoothAdapter.startDiscovery()
        }
    }

    fun stopDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun pairDevice(device: BluetoothDevice) {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        device.createBond()
    }

    fun onDiscoveryStarted() = _uiState.update { it.copy(isDiscovering = true) }
    fun onDiscoveryFinished() = _uiState.update { it.copy(isDiscovering = false) }
    fun onDeviceFound(device: BluetoothDevice) {
        if (device.name != null && device.address !in _uiState.value.discoveredDevices.map { it.address }) {
            _uiState.update { it.copy(discoveredDevices = it.discoveredDevices + device) }
        }
    }
    fun onBondStateChanged() {
        updatePairedDevices()
        _uiState.update { it.copy(discoveredDevices = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService?.stop()
    }
}


// --- Service for handling connections and data transfer ---
private enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, MESSAGE_RECEIVED }
@SuppressLint("MissingPermission")
private class BluetoothService(
    private val adapter: BluetoothAdapter,
    private val onStateChange: (ConnectionState, BluetoothDevice?, String?) -> Unit
) {
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    @Synchronized
    fun start() {
        stopAllThreads()
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        // 如果正在连接或已连接，忽略这次调用
        if (connectThread != null || connectedThread != null) {
            Log.w(TAG, "connect() called while already connecting or connected. Ignoring.")
            return
        }
        stopAllThreads()
        connectThread = ConnectThread(device)
        connectThread?.start()
        onStateChange(ConnectionState.CONNECTING, device, null)
    }

    @Synchronized
    fun stop() {
        stopAllThreads()
        onStateChange(ConnectionState.DISCONNECTED, null, null)
    }

    // 内部清理方法
    private fun stopAllThreads() {
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        acceptThread?.cancel()
        acceptThread = null
    }
    @Synchronized
    private fun connectionEstablished(socket: BluetoothSocket, device: BluetoothDevice) {

        acceptThread?.cancel()
        acceptThread = null

        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        onStateChange(ConnectionState.CONNECTED, device, null)
    }

    fun write(bytes: ByteArray) {
        connectedThread?.write(bytes)
    }

    private fun connectionLost() {
        Log.i(TAG,"connectionLost")
        stopAllThreads()
        onStateChange(ConnectionState.DISCONNECTED, null, null)
    }




    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? by lazy {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        }
        override fun run() {
            try {
                val socket = serverSocket?.accept()
                if (socket != null) {
                    connectionEstablished(socket, socket.remoteDevice)
                }
            } catch (e: IOException) {
                // 通常是 cancel() 导致的，属于正常关闭
            }
        }
        // cancel只关闭ServerSocket
        fun cancel() { try { serverSocket?.close() } catch (e: IOException) {Log.e(TAG, "Could not close the connect socket", e)} }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var mmSocket: BluetoothSocket? = null // 使用局部变量
        override fun run() {
            adapter.cancelDiscovery()
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                mmSocket?.connect()
                val socket = mmSocket ?: throw IOException("Socket is null")
                connectionEstablished(socket, device)
            } catch (e: IOException) {
                connectionLost()
                cancel() // 失败时清理自己
            }
        }
        // cancel只关闭自己的socket
        fun cancel() { try { mmSocket?.close() } catch (e: IOException) {Log.e(TAG, "", e)} }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private val buffer = ByteArray(1024)
        override fun run() {
            while (true) {
                try {
                    val numBytes = inputStream.read(buffer)
                    if (numBytes == -1) break
                    val msg = String(buffer, 0, numBytes)
                    onStateChange(ConnectionState.MESSAGE_RECEIVED, socket.remoteDevice, msg)
                } catch (e: IOException) {
                    break
                }
            }
            connectionLost()
        }
        fun write(bytes: ByteArray) { try { outputStream.write(bytes) } catch (e: IOException) {} }
        // cancel只关闭自己的socket
        fun cancel() { try { socket.close() } catch (e: IOException) {} }
    }
}