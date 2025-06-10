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
    // --- 定义状态类，用于通知ViewModel ---
    sealed class ManagerState {
        data class Error(val message: String) : ManagerState()
        data class Connecting(val device: BluetoothDevice) : ManagerState()
        data class Connected(val device: BluetoothDevice) : ManagerState()
        object Disconnected : ManagerState()
        data class MessageReceived(val message: String) : ManagerState()
        object ScanStarted : ManagerState()
        object ScanStopped : ManagerState()
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

        // 写特征 (Central -> Peripheral)
        val writeChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        // 通知特征 (Peripheral -> Central)
        val notifyChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // 为通知特征添加描述符
        val configDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(configDescriptor)

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)

        gattServer?.addService(service)
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

    // --- 通用方法 ---
    fun sendMessage(message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)

        // 作为Client(中心)发送
        gattClient?.let { gatt ->
            writeCharacteristic?.let { char ->
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(char)
            }
        }

        // 作为Server(外围)发送
        gattServer?.let { server ->
            connectedDevice?.let { device ->
                val notifyChar = server.getService(BleConstants.SERVICE_UUID)
                    ?.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
                notifyChar?.value = bytes
                server.notifyCharacteristicChanged(device, notifyChar, false)
            }
        }
    }

    fun disconnect() {
        gattClient?.disconnect()
        gattClient?.close()
        gattClient = null

        connectedDevice?.let { gattServer?.cancelConnection(it) }
        connectedDevice = null
    }

    fun close() {
        disconnect()
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        scanner?.stopScan(scanCallback)
    }

    // --- 回调区域 ---
    private val advertiseCallback = object : AdvertiseCallback() {}

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            coroutineScope.launch(Dispatchers.Main) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                    onStateChange(ManagerState.Connected(device))
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                    onStateChange(ManagerState.Disconnected)
                }
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_WRITE_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                val message = value.toString(Charsets.UTF_8)
                coroutineScope.launch(Dispatchers.Main) {
                    onStateChange(ManagerState.MessageReceived(message))
                }
            }
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            coroutineScope.launch(Dispatchers.Main) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onStateChange(ManagerState.Disconnected)
                    gattClient?.close()
                    gattClient = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            if (service == null) {
                Log.w("BLE", "Service not found!")
                gatt.disconnect()
                return
            }
            writeCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_WRITE_UUID)
            val notifyChar = service.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)

            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            coroutineScope.launch(Dispatchers.Main) {
                onStateChange(ManagerState.Connected(gatt.device))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.CHARACTERISTIC_NOTIFY_UUID) {
                val message = characteristic.value.toString(Charsets.UTF_8)
                coroutineScope.launch(Dispatchers.Main) {
                    onStateChange(ManagerState.MessageReceived(message))
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name != null) {
                coroutineScope.launch(Dispatchers.Main) {
                    onStateChange(ManagerState.DeviceFound(result.device))
                }
            }
        }
    }
}