package com.ame.tcp_udp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class TcpClient {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    fun create(remoteAddress: String, remotePort: Int) {
        socket = Socket(remoteAddress, remotePort)
        writer = PrintWriter(socket!!.outputStream, true)
        reader = BufferedReader(InputStreamReader(socket!!.inputStream))
    }

    fun send(messageTx: String) {
        writer?.println(messageTx)
    }

    fun receive(): String {
        return reader?.readLine() ?: ""

    }

    fun close() {
        writer?.close()
        reader?.close()
        socket?.close()
    }
}