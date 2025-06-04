package com.ame.tcp_udp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpSocket {
    private var isRunning=false
    var onMessageReceived: ((message: String, address: InetAddress, port: Int) -> Unit)? = null // 添加 address 和 port
    private var serverJob: Job? = null
    private var socket: DatagramSocket? = null
    private var ip :String?=null
    fun create(localPortToBind: Int? = null): Boolean { // localPortToBind可以为null，让系统分配临时端口
        try {
            socket = if (localPortToBind != null) {
                DatagramSocket(localPortToBind) // 服务器或需要特定本地端口的客户端
            } else {
                DatagramSocket() // 普通客户端，系统分配本地端口
            }
            // 检查socket是否成功创建并绑定
            if ( socket!!.isBound && !socket!!.isClosed) {
                isRunning = true
                Log.d("UdpSocket", "Socket opened and bound to local port: ${socket?.localPort}")
                return true
            }
            Log.e("UdpSocket", "Socket creation or binding failed.")
            return false
        } catch (e: Exception) {
            Log.e("UdpSocket", "Failed to create socket: ${e.message}", e)
            socket?.close() //确保异常时关闭
            socket = null
            return false
        }
    }

    fun send(message: String, clientAddress: InetAddress, clientPort: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = message.toByteArray()
                val packet = DatagramPacket(data, data.size, clientAddress, clientPort)
                socket?.send(packet)
                println("Sent: $message to ${clientAddress.hostAddress}:$clientPort")
            } catch (e: Exception) {
                println("Failed to send message: ${e.message}")
            }
        }
    }



    fun receive() {
        if (!isRunning || socket == null || socket!!.isClosed) {
            Log.w("UdpSocket", "Receive called but socket not running or null.")
            return
        }

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024) // 创建一次
            val packet = DatagramPacket(buffer, buffer.size) // 创建一次

            Log.d("UdpSocket", "Started receiving on port ${socket?.localPort}")
            while (isRunning && socket != null && !socket!!.isClosed) {
                try {
                    // packet.setData(buffer) // 如果重用packet且buffer大小可能变化，需要重设
                    // packet.setLength(buffer.size) // 确保packet的长度是整个buffer的大小
                    // DatagramPacket构造时已设置，一般不用重设，除非你改变了buffer

                    socket?.receive(packet) // 阻塞直到接收到数据或socket关闭

                    // 检查socket是否在我们等待接收时被关闭了
                    if (!isRunning || socket == null || socket!!.isClosed) break

                    val message = String(packet.data, 0, packet.length)
                    val clientAddress = packet.address
                    val clientPort = packet.port

                    Log.d("UdpSocket", "Received: \"$message\" from ${clientAddress.hostAddress}:$clientPort")

                    // 触发回调
                    onMessageReceived?.invoke(message, clientAddress, clientPort)

                    // 可选: 清空buffer或重置packet长度，如果下次预期数据更短，避免旧数据残留影响 String 转换
                    // Arrays.fill(buffer, 0.toByte()) // 或 packet.length = buffer.size;

                } catch (e: SocketTimeoutException) {
                    // 只有设置了socket.setSoTimeout()才可能发生
                    Log.d("UdpSocket", "Socket timeout during receive, continuing...")
                } catch (e: IOException) {
                    if (isRunning) { // 如果仍然是isRunning状态，说明不是主动关闭导致的异常
                        Log.e("UdpSocket", "Receive error: ${e.message}", e)
                    } else {
                        Log.d("UdpSocket", "Socket closed, receive loop terminating.")
                    }
                    break // 发生IO异常（如socket被关闭）时，退出循环
                }
            }
            Log.d("UdpSocket", "Receive loop finished.")
        }
    }

    // 建议修改
    fun close() {
        Log.d("UdpSocket", "Closing UDP socket...")
        isRunning = false // 先改变状态，让循环条件不满足
        serverJob?.cancel() // 取消接收协程
        try {
            socket?.close() // 关闭socket，这将中断阻塞的receive()
        } catch (e: Exception) {
            Log.e("UdpSocket", "Error closing socket: ${e.message}", e)
        }
        socket = null
        serverJob = null
        Log.i("UdpSocket", "UDP Socket closed")
    }

}