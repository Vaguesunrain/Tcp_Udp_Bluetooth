package com.ame.tcp_udp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpServer :SocketOperations {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    override fun create(ip:String, port: Int): Boolean {
        try {
            serverSocket = ServerSocket(port)
            clientSocket = serverSocket!!.accept()
            reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
            writer = PrintWriter(clientSocket!!.getOutputStream(), true)
            return true
        }
        catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    override fun send(data: String) :Boolean{
        writer!!.println(data)
        return  true
    }

    override fun receive(): String {
        return reader!!.readLine()
    }

    override fun close() {
        reader!!.close()
        writer!!.close()
        clientSocket!!.close()
        serverSocket!!.close()
    }
    override fun isConnected(): Boolean {
        return true
    }
}