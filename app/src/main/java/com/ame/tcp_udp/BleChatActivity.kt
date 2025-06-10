// BleChatActivity.kt
package com.ame.tcp_udp
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ame.tcp_udp.BlueTooth.BleViewModel // <-- 引用新的ViewModel
import com.ame.tcp_udp.BlueTooth.BleUiState // <-- 引用新的UI State
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme

class BleChatActivity : ComponentActivity() {

    private val viewModel: BleViewModel by lazy {
        BleViewModel.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startMode = intent.getStringExtra("START_MODE")
        if (startMode == "SERVER") {
            // 如果是以服务器(外围设备)模式启动，则调用ViewModel开启广播
            viewModel.startServerMode()
        }

        // 注意：在BLE中，客户端(中心设备)模式通常由用户在另一个屏幕上选择设备后启动，
        // 这里为了简化，我们假设如果不是SERVER，就是等待连接。
        // 一个完整的App会有一个扫描界面。

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

// ChatScreen Composable 基本可以保持原样，因为它是响应式的。
// 只需要确保它使用的 uiState 是 BleUiState 类型。
// 我们将 `BluetoothUiState` 重命名为 `BleUiState`。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: BleUiState, // <-- 使用新的State
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onConnectionLostAndConfirmed: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showConnectionLostDialog by remember { mutableStateOf(false) }
    var deviceNameForDialog by remember { mutableStateOf<String?>(null) }

    // ... (LaunchedEffect 和 AlertDialog 逻辑保持不变)
    // ... (UI 布局，如Scaffold, TopAppBar, TextField等也保持不变)

    // 我们只需要修改等待连接时的提示文本
    if (!uiState.isConnecting && !uiState.isConnected && uiState.connectedDeviceName == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                // 根据模式显示不同文本
                val waitingText = if (uiState.isServer) "正在广播，等待客户端连接..." else "正在扫描/等待连接..."
                Text(waitingText)
            }
        }
        return
    }

    // 完整的 ChatScreen Composable 代码... (和你的原版一样)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
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
            reverseLayout = true
        ) {
            items(uiState.messages.reversed()) { message ->
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