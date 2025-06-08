package com.ame.tcp_udp;

import com.ame.tcp_udp.BlueTooth.BluetoothViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ame.tcp_udp.BlueTooth.BluetoothUiState
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme

class BluetoothChatActivity : ComponentActivity() {

//    private val viewModel: BluetoothViewModel by viewModels()
    private val viewModel: BluetoothViewModel by lazy {
        BluetoothViewModel.getInstance(application)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startMode = intent.getStringExtra("START_MODE")
        if (startMode == "SERVER") {
            // 如果是以服务器模式启动，则调用ViewModel开启服务器
            viewModel.startServer()
        }
        setContent {
            Tcp_UdpTheme {
                val uiState by viewModel.uiState.collectAsState()
                ChatScreen(
                    uiState = uiState,
                    onSendMessage = viewModel::sendMessage,
                    onDisconnect = {
                        viewModel.disconnect()
                        finish()
                    },
                    onConnectionLostAndConfirmed = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.disconnect()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: BluetoothUiState,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onConnectionLostAndConfirmed: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showConnectionLostDialog by remember { mutableStateOf(false) }

    // 用来“记住”我们断开连接前的设备名
    var deviceNameForDialog by remember { mutableStateOf<String?>(null) }

    // 第一个 LaunchedEffect: 它的唯一职责是当连接建立时，记住设备名。
    // 它只在 connectedDeviceName 变化时触发。
    LaunchedEffect(uiState.connectedDeviceName) {
        if (uiState.isConnected && uiState.connectedDeviceName != null) {
            deviceNameForDialog = uiState.connectedDeviceName
        }
    }

    LaunchedEffect(uiState.isConnected) {
        // 如果连接断开了，并且我们之前“记住”了一个设备名
        if (!uiState.isConnected && deviceNameForDialog != null) {
            showConnectionLostDialog = true
        }
    }
    if (showConnectionLostDialog) {
        AlertDialog(
            onDismissRequest = {
                // 不允许通过点击外部来关闭弹窗，强制用户确认
            },
            title = { Text("Lose connection") },
            text = { Text("Connection lost. Navigating back to the previous screen. ") },
            confirmButton = {
                Button(onClick = {
                    showConnectionLostDialog = false
                    onConnectionLostAndConfirmed() // 调用回调来关闭Activity
                }) {
                    Text("确认")
                }
            }
        )
    }

    if (!uiState.isConnecting && !uiState.isConnected && uiState.connectedDeviceName == null) {

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("正在等待客户端连接...")
            }
        }
        return
    }
Scaffold(
        topBar = {
    TopAppBar(
            title = { Text(text = "Chat with ${uiState.connectedDeviceName ?: "..."}") },
    actions = {
            Button(onClick = onDisconnect) {
            Text("Disconnect")
    }
    }
            )
    },
    bottomBar = {
        Column(
            modifier = Modifier.fillMaxWidth(), // Column 填满宽度
            horizontalAlignment = Alignment.CenterHorizontally // Column 中的内容水平居中
        ){
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter message") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = uiState.isConnected && messageText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Message")
                }
            }
        }

        }
        ) { paddingValues ->
LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
reverseLayout = true // To show latest messages at the bottom
        ) {
items(uiState.messages.reversed()) { message ->
// A simple way to show who sent the message
val alignment = if (message.startsWith("Me:")) Alignment.End else Alignment.Start
val text = message.substringAfter(":")
val prefix = if (message.startsWith("Me:")) "Me" else uiState.connectedDeviceName ?: "Them"

Column(
        modifier = Modifier.fillMaxWidth(),
horizontalAlignment = alignment
                ) {
Text(text = prefix, style = MaterialTheme.typography.labelSmall)
Text(
        text = text,
        modifier = Modifier.padding(vertical = 4.dp),
style = MaterialTheme.typography.bodyLarge
                    )
                            }

                            }
                            }
                            if (!uiState.isConnected) {
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
Text("Connection Lost.")
            }
                    }
                    }
                    }