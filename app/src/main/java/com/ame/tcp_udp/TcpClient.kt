package com.ame.tcp_udp

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class TcpClient :SocketOperations{
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
//    private var outputStream: DataOutputStream? = null
    override fun create(ip: String, port: Int): Boolean {
        try {
            Log.i("test" ,"$ip , $port")
            socket = Socket(ip, port)
//            outputStream = DataOutputStream(socket!!.outputStream)
            writer = PrintWriter(socket!!.outputStream, true)
            reader = BufferedReader(InputStreamReader(socket!!.inputStream))
            return true
        }
        catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }


    override fun send(data: String): Boolean{
        if (writer == null || socket?.isClosed == true || !isConnected()) {
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
    override fun receive(): String? { // 要求服务器发送回车
        if (reader == null || socket?.isClosed == true || !isConnected()) {
            return null
        }
        return try {
            val line = reader?.readLine() // readLine() 是阻塞的
            if (line == null) {
                Log.i("TAG", "Stream closed by peer or end of stream.")
                // 通常意味着连接已断开，可以考虑在这里也调用 close() 或者让外部逻辑处理
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
        Log.i("get","shutdown")
        writer?.close()
        reader?.close()
        socket?.close()
    }
    override fun isConnected(): Boolean {
        return socket != null && socket!!.isConnected && !socket!!.isClosed
    }
    override fun waitConnect(): String {
        return "OK"
    }
}


