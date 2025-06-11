package com.ame.tcp_udp.BlueTooth


import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
@SuppressLint("MissingPermission")
class BleChatManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onStateChange: (ManagerState) -> Unit
) {

    companion object {
        // A unique command to prevent accidental disconnection if a user types "DISCONNECT"
        const val DISCONNECT_MESSAGE = "CUSTOM_BLE_DISCONNECT_COMMAND_A7B3"
    }
    // --- 定义状态类，用于通知ViewModel ---
    sealed class ManagerState {
        data class Error(val message: String) : ManagerState()
        data class Connecting(val device: BluetoothDevice) : ManagerState()
        data class Connected(val device: BluetoothDevice) : ManagerState()
        data object Disconnected : ManagerState()
        data class MessageReceived(val message: String) : ManagerState()
        data object ScanStarted : ManagerState()
        data object ScanStopped : ManagerState()
        data class DeviceFound(val device: BluetoothDevice) : ManagerState()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // --- GATT Server (Peripheral Role) 相关 ---
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private var connectedDevice: BluetoothDevice? = null

    // --- GATT Client (Central Role) 相关 ---
    private var gattClient: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // --- Server (Peripheral) 模式方法 ---
    fun startServer() {
        if (advertiser == null || bluetoothAdapter?.isMultipleAdvertisementSupported == false) {
            onStateChange(ManagerState.Error("BLE Advertising not supported"))
            return
        }
        setupGattServer()
        startAdvertising()
    }

    private fun setupGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // 【修复1】写特征 (Central -> Peripheral) - 添加读权限
        val writeChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        // 【修复2】通知特征 (Peripheral -> Central) - 确保正确的属性和权限
        val notifyChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // 【修复3】为通知特征添加描述符 - 确保客户端可以订阅通知
        val configDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(configDescriptor)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)

        val addServiceResult = gattServer?.addService(service)
        Log.d("BLE_SERVER", "Add service result: $addServiceResult")
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(BleConstants.SERVICE_PARCEL_UUID)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d("BLE_SERVER", "Started advertising")
    }

    // --- Client (Central) 模式方法 ---
    fun startScan() {
        if (scanner == null) {
            onStateChange(ManagerState.Error("BLE Scanning not supported"))
            return
        }
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SERVICE_PARCEL_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(scanFilter), settings, scanCallback)
        onStateChange(ManagerState.ScanStarted)
    }

    fun connectToDevice(device: BluetoothDevice) {
        scanner?.stopScan(scanCallback)
        onStateChange(ManagerState.ScanStopped)
        onStateChange(ManagerState.Connecting(device))
        gattClient = device.connectGatt(context, false, gattClientCallback)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        onStateChange(ManagerState.ScanStopped)
    }

    // --- 通用方法 ---
    fun sendMessage(message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        Log.d("BLE_SEND", "Attempting to send message: $message")

        // 作为Client(中心)发送
        gattClient?.let { gatt ->
            writeCharacteristic?.let { char ->
                Log.d("BLE_SEND", "Sending as client to server")
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val writeResult = gatt.writeCharacteristic(char)
                Log.d("BLE_SEND", "Client write result: $writeResult")
            } ?: Log.e("BLE_SEND", "Write characteristic is null!")
        }

        // 作为Server(外围)发送
        gattServer?.let { server ->
            connectedDevice?.let { device ->
                Log.d("BLE_SEND", "Sending as server to client")
                val notifyChar = server.getService(BleConstants.SERVICE_UUID)
                    ?.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
                if (notifyChar != null) {
                    notifyChar.value = bytes
                    val notifyResult = server.notifyCharacteristicChanged(device, notifyChar, false)
                    Log.d("BLE_SEND", "Server notify result: $notifyResult")
                } else {
                    Log.e("BLE_SEND", "Notify characteristic not found!")
                }
            } ?: Log.e("BLE_SEND", "No connected device!")
        }
    }

    fun disconnect() {
        Log.d("BLE_DISCONNECT", "Initiating disconnect...")

        // 客户端断开
        gattClient?.let { client ->
            Log.d("BLE_DISCONNECT", "Disconnecting as client...")
            client.disconnect()
            // 不要立即close，让回调处理
        }

        // 服务端断开
        gattServer?.let { server ->
            connectedDevice?.let { device ->
                Log.d("BLE_DISCONNECT", "Disconnecting as server from device: ${device.address}")
                // 【关键修复】使用正确的服务端断开方法
                server.cancelConnection(device)
            }
        }
    }

    fun close() {
        disconnect()
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        scanner?.stopScan(scanCallback)
    }

    // --- 回调区域 ---
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLE_ADV", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_ADV", "Advertising failed with error: $errorCode")
            onStateChange(ManagerState.Error("Advertising failed: $errorCode"))
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("BLE_SERVER", "Connection state changed: status=$status, newState=$newState")
            coroutineScope.launch(Dispatchers.Main) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    advertiser?.stopAdvertising(advertiseCallback) // stop broadcast
                    connectedDevice = device
                    Log.d("BLE_SERVER", "Client connected: ${device.name ?: device.address}")
                    onStateChange(ManagerState.Connected(device))
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                    gattServer?.close()
                    gattServer = null // 将引用置空，防止后续误用
                    Log.d("BLE_SERVER", "Client disconnected")
                    onStateChange(ManagerState.Disconnected)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {

            if (characteristic.uuid == BleConstants.CHARACTERISTIC_WRITE_UUID) {
                if (responseNeeded) {
                    val response = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    Log.d("BLE_SERVER", "Sent response: $response")
                }

                // 处理接收到的消息
                val message = value.toString(Charsets.UTF_8)
                Log.d("BLE_SERVER", "Received message: $message")
                coroutineScope.launch(Dispatchers.Main) {
                    onStateChange(ManagerState.MessageReceived(message))
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                }
                Log.w("BLE_SERVER", "Unexpected characteristic write: ${characteristic.uuid}")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d("BLE_SERVER", "Descriptor write request from ${device.address}")
            if (descriptor.uuid == BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                Log.d("BLE_SERVER", "Client subscribed to notifications")
            }
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE_CLIENT", "Connection state changed: status=$status, newState=$newState")
            coroutineScope.launch(Dispatchers.Main) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE_CLIENT", "Connected to server, discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE_CLIENT", "Disconnected from server")
                    onStateChange(ManagerState.Disconnected)
                    gattClient?.close()
                    gattClient = null
                    writeCharacteristic = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BLE_CLIENT", "Services discovered, status: $status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE_CLIENT", "Service discovery failed")
                gatt.disconnect()
                return
            }

            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                Log.e("BLE_CLIENT", "Service not found!")
                gatt.disconnect()
                return
            }

            writeCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_WRITE_UUID)
            if (writeCharacteristic != null) {
                Log.d("BLE_CLIENT", "SUCCESS: Write Characteristic found!")
            } else {
                Log.e("BLE_CLIENT", "FAILURE: Write Characteristic NOT found!")
                gatt.disconnect()
                return
            }
            // 设置通知
            val notifyChar = service.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
            if (notifyChar != null) {
                val setNotificationResult = gatt.setCharacteristicNotification(notifyChar, true)
                Log.d("BLE_CLIENT", "Set notification result: $setNotificationResult")

                // 写入描述符以启用通知
                val descriptor = notifyChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val writeDescResult = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        Log.d("BLE_CLIENT", "Write descriptor result (API 33+): $writeDescResult")
                    } else {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val writeDescResult = gatt.writeDescriptor(descriptor)
                        Log.d("BLE_CLIENT", "Write descriptor result (Legacy): $writeDescResult")
                    }
                } else {
                    Log.e("BLE_CLIENT", "Descriptor not found!")
                    gatt.disconnect()
                }
            } else {
                Log.e("BLE_CLIENT", "Notify characteristic not found!")
                gatt.disconnect()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("BLE_CLIENT", "Descriptor write completed, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.uuid == BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    Log.d("BLE_CLIENT", "Notification subscription successful. Connection fully ready.")
                    coroutineScope.launch(Dispatchers.Main) {
                        onStateChange(ManagerState.Connected(gatt.device))
                    }
                }
            } else {
                Log.e("BLE_CLIENT", "Failed to write descriptor, status: $status")
                gatt.disconnect()
            }
        }

        // 写特征完成的回调处理
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_CLIENT", "Message sent successfully")
            } else {
                Log.e("BLE_CLIENT", "Failed to send message, status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_NOTIFY_UUID) {
                val message = characteristic.value.toString(Charsets.UTF_8)
                Log.d("BLE_CLIENT", "Received notification: $message")
                if (message == DISCONNECT_MESSAGE) {
                    Log.i("BLE_CLIENT", "Disconnect command received from server. Initiating disconnect.")
                    // The client now initiates its own disconnection sequence.
                    disconnect()
                } else {
                    // It's a regular chat message, notify the ViewModel to display it.
                    coroutineScope.launch(Dispatchers.Main) {
                        onStateChange(ManagerState.MessageReceived(message))
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("BLE_SCAN", "Found device: ${result.device.name ?: "Unknown"} (${result.device.address})")
            coroutineScope.launch(Dispatchers.Main) {
                onStateChange(ManagerState.DeviceFound(result.device))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE_SCAN", "Scan failed with error code: $errorCode")
            onStateChange(ManagerState.Error("Scan failed with code: $errorCode"))
        }
    }
}