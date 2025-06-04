package com.ame.tcp_udp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class ActivityUdp : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var serOrClient by remember { mutableStateOf(false) }
            var port by remember { mutableIntStateOf(0) }
            var ip by remember { mutableStateOf("") }
            val messageIp = intent.getStringExtra("IP_ADDRESS") ?: "" // Get IP address, default to empty string if null
            if (messageIp.isNotEmpty()) {
                if (messageIp[0] == 'S') {
                    serOrClient = true
                    port=messageIp.substring(8).toInt()
                    ip = getLocalIpAddress(LocalContext.current)!!
                } else if (messageIp[0] == 'C') {
                    serOrClient = false
                    ip = messageIp.substring(8)
                    port = ip.substring(ip.indexOf(':') + 1).toInt()
                    ip = ip.substring(0, ip.indexOf(':'))
                }
            }
            Tcp_UdpTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.statusBars) { innerPadding ->
                    WorkUIUdp(
                        modifier = Modifier.padding(innerPadding),serOrClient,ip,port
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkUIUdp(modifier: Modifier = Modifier, serOrClient: Boolean, theIp: String, thePort: Int) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (serOrClient) {
                        Text(text = "Now work on UDP Server " , fontSize = 25.sp)
                    } else {
                        Text(text = "Now work on UDP Client",fontSize = 25.sp)
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
            val body = remember{ UdpSocket() }
            var sendMassage by remember { mutableStateOf("") }
            val messagesList = remember { mutableStateListOf<String>() }
            var connectState by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            var serverJob by remember { mutableStateOf<Job?>(null) }
            Row {
                Column {
                    Text(text = "Local IP is $theIp ")
                    Text(text = "Port is $thePort")
                }
                Spacer(modifier.width(70.dp))

                Button(onClick = {
                        if (!connectState){
                            serverJob?.cancel()
                            serverJob = scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    body.create(thePort)
                                }
                                if(success){
                                    connectState = true
                                    body.onMessageReceived = { message, address, port ->
                                        scope.launch(Dispatchers.Main) { // 或直接添加，mutableStateListOf是线程安全的
                                            messagesList.add("From ${address.hostAddress}:$port: $message")
                                            // 如果是服务器，可能需要保存最新的客户端地址用于回复
                                            if (serOrClient) {
                                                // Consider storing address and port if you want to "reply" via send button
                                                // e.g., lastClientAddress = address; lastClientPort = port;
                                            }
                                        }
                                    }
                                    body.receive() // 开始接收
                                }
                            }
                        }
                        else{
                            connectState = false
                            serverJob?.cancel() // 取消正在进行的接收任务
                            scope.launch(Dispatchers.IO) { // 在 IO 线程关闭
                                body.close()

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
                placeholder = {  Text("Please enter message") },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row() {
                Button(modifier= Modifier
                    .width(120.dp)
                    .height(50.dp), onClick = {
                    Log.i("get", sendMassage)
                    if (connectState ){
                        Log.i("get","haha11")
                        body.send(sendMassage, InetAddress.getByName(theIp), thePort)
                        messagesList.add("Me: $sendMassage")
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
                Text(text = "$connectState ")
            }
        }
    }
}


