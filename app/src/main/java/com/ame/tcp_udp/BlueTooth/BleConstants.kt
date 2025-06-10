package com.ame.tcp_udp.BlueTooth

import android.os.ParcelUuid
import java.util.*

object BleConstants {
    // 为你的服务创建一个唯一的UUID
    val SERVICE_UUID: UUID = UUID.fromString("0000a0a0-0000-1000-8000-00805f9b34fb")//注意会不会冲突//ToDo

    // 为“写入”特征创建UUID (Central -> Peripheral)
    val CHARACTERISTIC_WRITE_UUID: UUID = UUID.fromString("0000a0a1-0000-1000-8000-00805f9b34fb")

    // 为“通知”特征创建UUID (Peripheral -> Central)
    val CHARACTERISTIC_NOTIFY_UUID: UUID = UUID.fromString("0000a0a2-0000-1000-8000-00805f9b34fb")

    // 用于客户端描述符，以启用通知
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)
}