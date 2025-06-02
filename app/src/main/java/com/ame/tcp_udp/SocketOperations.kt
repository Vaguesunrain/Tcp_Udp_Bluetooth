package com.ame.tcp_udp

// 定义一个接口，包含 Client 和 Server 共有的方法
interface SocketOperations {
    fun create(ip: String, port: Int): Boolean // 假设 create 返回一个布尔值表示成功与否
    fun send(data: String): Boolean
    fun receive(): String?
    fun close()
    abstract fun isConnected(): Boolean
    // 你可以添加其他所有共有的方法
}