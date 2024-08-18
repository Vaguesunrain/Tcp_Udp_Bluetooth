package com.ame.tcp_udp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpServer {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    fun create(port: Int) {
        serverSocket = ServerSocket(port)
        clientSocket = serverSocket!!.accept()
        reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
        writer = PrintWriter(clientSocket!!.getOutputStream(), true)
    }

    fun send(message: String) {
        writer!!.println(message)
    }

    fun receive(): String {
        return reader!!.readLine()
    }

    fun close() {
        reader!!.close()
        writer!!.close()
        clientSocket!!.close()
        serverSocket!!.close()
    }
}