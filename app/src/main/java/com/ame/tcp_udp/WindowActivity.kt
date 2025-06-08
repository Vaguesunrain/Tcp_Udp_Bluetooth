package com.ame.tcp_udp
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ame.tcp_udp.socket.SocketOperations
import com.ame.tcp_udp.socket.TcpClient
import com.ame.tcp_udp.socket.TcpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import kotlin.coroutines.cancellation.CancellationException


class WindowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var SerOrClient by remember { mutableStateOf(false) }
            var port by remember { mutableIntStateOf(0) }
            var ip by remember { mutableStateOf("") }
            val messageIp = intent.getStringExtra("IP_ADDRESS") ?: "" // Get IP address, default to empty string if null
            if (messageIp.isNotEmpty()) {
                if (messageIp[0] == 'S') {
                    SerOrClient = true
                    port=messageIp.substring(8).toInt()
                    ip = getLocalIpAddress(LocalContext.current)!!

                } else if (messageIp[0] == 'C') {
                    SerOrClient = false
                    ip = messageIp.substring(8)
                    port = ip.substring(ip.indexOf(':') + 1).toInt()
                    ip = ip.substring(0, ip.indexOf(':'))
                }
            }
            Tcp_UdpTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.statusBars) { innerPadding ->
                    WorkUI(
                        modifier = Modifier.padding(innerPadding),SerOrClient,ip,port
                    )
                }
            }
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkUI( modifier: Modifier = Modifier ,SerOrClient: Boolean , theIp: String ,thePort: Int) {
//    注意SerOrClient=1时是服务器
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (SerOrClient) {
                        Text(text = "Now work on TCP Server " , fontSize = 25.sp)
                    } else {
                        Text(text = "Now work on TCP Client",fontSize = 25.sp)
                    }
                },
                modifier = Modifier.height(100.dp)

            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding) // This ensures content is below topBar and above navBar
                .fillMaxSize() ,// It's often good to fill the available space
                horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val body = remember(SerOrClient) { createSocket(SerOrClient) }
            var sendMassage by remember { mutableStateOf("") }
            val messagesList = remember { mutableStateListOf<String>() }
            var connectState by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            var receiveJob by remember { mutableStateOf<Job?>(null) }
            var serverJob by remember { mutableStateOf<Job?>(null) }
            Row {
                Column {
                        Text(text = if(SerOrClient)"Local IP is $theIp " else "Remote IP is $theIp ")
                        Text(text = "Port is $thePort")
                }
                Spacer(modifier.width(70.dp))
                if(SerOrClient){
                   Button(onClick = {
                       if (!connectState){
                           serverJob?.cancel()
                           serverJob = scope.launch {
                               val success = withContext(Dispatchers.IO) {
                                   body.create(theIp, thePort)
                               }
                               if(success)
                                   connectState = true
                               outerLoop@ while (success&&connectState) {

                                   messagesList.add("SYSTEM:Server is open, wait client connecting")
                                   val linkMessage = withContext(Dispatchers.IO) {
                                       body.waitConnect()
                                   }
                                   if (linkMessage != "error") {
                                       messagesList.add("SYSTEM: "+linkMessage + "is connecting")
                                   } else {
                                       messagesList.add("SYSTEM:Someone tried to connect, but failed")
                                   }
                                   scope.launch(Dispatchers.IO) { // Launch a dedicated reader coroutine on IO thread
                                       try {
                                           while (isActive) {
                                               val messageFromClient = body.receive() // This is a blocking network call

                                               if (messageFromClient != null) {

                                                   withContext(Dispatchers.Main) {
                                                       messagesList.add("Client: $messageFromClient")
                                                   }
                                               } else {
                                                   withContext(Dispatchers.Main) {
                                                       messagesList.add("SYSTEM:Client disconnected gracefully.")
                                                   }
                                                   break
                                               }
                                           }
                                       } catch (e: java.io.IOException) {
                                           withContext(Dispatchers.Main) {
                                               messagesList.add("Client connection error/closed: ${e.message}")
                                           }
                                       } catch (e: Exception) {
                                           withContext(Dispatchers.Main) {
                                               messagesList.add("Error reading from client: ${e.message}")
                                           }
                                       } finally {
                                           withContext(Dispatchers.Main) {
                                               messagesList.add("Stopped reading from client.")
                                           }
                                       }
                                   }
                               }
                           }
                       }
                       else{
                           connectState = false
                           serverJob?.cancel() // 取消正在进行的接收任务
                           scope.launch(Dispatchers.IO) { // 在 IO 线程关闭
                               body.close()
                               messagesList.add("SYSTEM:Cancel the Server")
                           }
                       }
                   }) {
                       Text(
                           if (connectState)
                               "Disconnect"
                           else
                               "Connect"
                       )
                   }
                }
                else
                Button(onClick = {
                    if (!connectState){
                        receiveJob?.cancel()
                        scope.launch { // 启动一个新的协程
                            val success = withContext(Dispatchers.IO) { // 切换到 IO 线程执行网络操作
                                body.create(theIp, thePort)
                            }
                            if(success){//开启read
                                connectState = true
                                receiveJob = launch(Dispatchers.IO) {
                                    try {
                                        while (isActive && body.isConnected()) { // isActive 检查协程是否还在活动
                                            val receivedMessage = body.receive() // 这是阻塞调用
                                            if (receivedMessage != null) {
                                                withContext(Dispatchers.Main) { // 切换回主线程更新 UI
                                                    messagesList.add("Server: $receivedMessage")
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    connectState = false // 更新连接意图状态
                                                    body.close() // 确保关闭
                                                }
                                                break // 退出接收循环
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        Log.i("TCP_RECEIVE", "Receive job cancelled.")
                                        // 不需要特别处理，协程被取消是正常流程
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            connectState = false
                                        }
                                    } finally {
                                        if (body.isConnected()) { // 避免重复调用 close
                                            Log.d("TCP_RECEIVE_FINALLY", "Closing body from receiveJob finally block.")
                                        }
                                        if (isActive) {
                                            withContext(Dispatchers.Main) {
                                                connectState = false
                                            }
                                        }
                                    }
                                }
                            }
                            else {
                                Log.e("TCP_CONNECT", "Failed to create connection.")
                                connectState = false // 连接失败，重置意图
                            }
                        }
                    }
                    else{
                        connectState = false
                        receiveJob?.cancel() // 取消正在进行的接收任务
                        scope.launch(Dispatchers.IO) { // 在 IO 线程关闭
                            body.close()
                            withContext(Dispatchers.Main) {
                                Log.i("TCP_DISCONNECT", "Connection closed by user.")
                                // messagesList.clear() // 可以在这里清空，或者在连接时清空
                            }
                        }
                    }
                }) {
                    Text(
                        if (connectState)
                            "Disconnect"
                        else
                            "Connect"
                    )
                }
            }
            Spacer(modifier.heightIn(10.dp))
            MessageListDisplay(messagesList )
            Spacer(modifier.heightIn(5.dp))
            TextField(
                value = sendMassage,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done // 将回车键设置为“完成”
                ),
                onValueChange = { sendMassage = it },modifier = Modifier
                    .offset(y = 4.dp, x = (-3).dp)
                    .fillMaxWidth(0.8f),
                placeholder = {  Text("Please enter message")},
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row() {
                Button(modifier= Modifier
                    .width(120.dp)
                    .height(50.dp), onClick = {
                        if (connectState ){
                            val messageToSendCurrent = sendMassage // 捕获当前值，避免协程中值改变
                            if (messageToSendCurrent.isNotBlank()) {
                                scope.launch { // 启动协程
                                    val success = withContext(Dispatchers.IO) { // 切换到 IO 线程执行网络操作
                                        body.send(messageToSendCurrent)
                                    }
                                    // 操作完成后，回到主线程更新 UI (如果需要)
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            messagesList.add("Me: $messageToSendCurrent") // 假设 messagesList 是 mutableStateListOf
                                        } else {
                                            Log.i("get", "Failed to send message: $messageToSendCurrent")
                                        }
                                    }
                                }
                            } else {
                                Log.w("TCP_SEND", "Cannot send empty message.")
                                // Optionally show a Toast to the user
                            }
                        }
                        else{
                            Log.i("get","something is false")
                        }
                        }) {
                    Text(text = "Send")
                }
                Spacer(modifier = Modifier.padding(20.dp))
                Button(modifier= Modifier
                    .width(120.dp)
                    .height(50.dp), onClick = { /*TODO*/ }) {
                    Text(text = "Save text")
                }
            }
        }
    }
}

@Composable
fun MessageListDisplay(messageList: List<String>) {
    Card {
        LazyColumn(
            modifier = Modifier

                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.7f)
        ) {
            items(messageList) { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth() // Make each Text take the full width of the LazyColumn
                        .padding(3.dp) // Add some padding around each message
                )
            }
        }
    }

}


fun createSocket(isServer:Boolean): SocketOperations { // 返回类型是接口
    return if (isServer) {
        TcpServer()
    } else {
        TcpClient()
    }
}

fun getLocalIpAddress(context: Context): String? {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val currentNetwork = connectivityManager.activeNetwork
        ?: return null // No active network

    val networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork) ?: return null
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        // Connected to Wi-Fi, try to get Wi-Fi IP
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddressInt = wifiInfo.ipAddress
        if (ipAddressInt != 0) {
            return String.format(
                Locale.getDefault(),
                "%d.%d.%d.%d",
                ipAddressInt and 0xff,
                ipAddressInt shr 8 and 0xff,
                ipAddressInt shr 16 and 0xff,
                ipAddressInt shr 24 and 0xff
            )
        }
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IP Address", "Error getting mobile IP address", e)
        }
    }
    try {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            val inetAddresses = networkInterface.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        Log.e("IP Address", "Error getting IP address", e)
    }

    return null
}

