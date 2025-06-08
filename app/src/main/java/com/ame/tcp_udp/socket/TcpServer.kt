package com.ame.tcp_udp.socket

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpServer : SocketOperations {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    override fun create(ip:String, port: Int): Boolean {
        try {
            serverSocket = ServerSocket(port)
            return true
        }
        catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    override fun waitConnect(): String {
        try{
            clientSocket = serverSocket!!.accept()
            val clientInetAddress = clientSocket?.inetAddress
            val clientIpAddress = clientInetAddress?.hostAddress.toString()
            reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
            writer = PrintWriter(clientSocket!!.getOutputStream(), true)
            return clientIpAddress
        }
        catch (e: Exception) {
            e.printStackTrace()
            return "error"
        }
    }
    override fun send(data: String): Boolean{
        if (writer == null || clientSocket?.isClosed == true || !isConnected()) {
            return false
        }
        return try {
            val messageWithNewline = data + "\n"
            writer?.println(messageWithNewline)
            // writer?.flush()
            if (writer?.checkError() == true) {
                // close()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            // close()
            false
        }

    }
    override fun receive(): String? {
        if (reader == null || clientSocket?.isClosed == true || !isConnected()) {
            return null
        }
        return try {
            val line = reader?.readLine()
            if (line == null) {
                Log.i("TAG", "Stream closed by peer or end of stream.")
            }
            line
        } catch (e: IOException) {
            close() // 出错时关闭连接
            null
        } catch (e: Exception) {
            close()
            null
        }
    }


    override fun close() {
        try {
            reader?.close() // Safe call: only closes if reader is not null
            Log.d("TCP_BODY_CLOSE", "Reader closed.")
        } catch (e: Exception) {
            Log.e("TCP_BODY_CLOSE", "Error closing reader: ${e.message}", e)
        }
        reader = null // Set to null after attempting close

        try {
            writer?.close()
            Log.d("TCP_BODY_CLOSE", "Writer closed.")
        } catch (e: Exception) {
            Log.e("TCP_BODY_CLOSE", "Error closing writer: ${e.message}", e)
        }
        writer = null

        try {
            clientSocket?.close()
            Log.d("TCP_BODY_CLOSE", "Client socket closed.")
        } catch (e: Exception) {
            Log.e("TCP_BODY_CLOSE", "Error closing client socket: ${e.message}", e)
        }
        clientSocket = null

        try {
            serverSocket?.close()
            Log.i("TCP_BODY_CLOSE", "Server socket closed.")
        } catch (e: Exception) {
            Log.e("TCP_BODY_CLOSE", "Error closing server socket: ${e.message}", e)
        }
        serverSocket = null

    }
    override fun isConnected(): Boolean {
        return true
    }
}