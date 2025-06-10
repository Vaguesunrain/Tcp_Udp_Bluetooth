// BleChatActivity.kt
package com.ame.tcp_udp
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
import com.ame.tcp_udp.BlueTooth.BleViewModel
import com.ame.tcp_udp.BlueTooth.BleUiState
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
                    onDialogConfirmed = {

                        Log.i("BleChatActivity", "onDialogConfirmed")
                        // 无论服务器还是客户端，确认对话框后都结束Activity
                        finish()
                    },

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

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: BleUiState,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDialogConfirmed: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showConnectionLostDialog by remember { mutableStateOf(false) }
    var deviceNameForDialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.connectedDevice) {
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
        val deviceName = uiState.connectedDevice?.name ?: "the other device"
        AlertDialog(
            onDismissRequest = { onDialogConfirmed() },
            title = { Text("Connection Terminated") },
            text = { Text("The connection with '$deviceName' was lost or could not be established.") },
            confirmButton = {
                Button(onClick = {showConnectionLostDialog=false
                    onDialogConfirmed() }) {
                    Text("Confirm")
                }
            }
        )
    }
    if (!uiState.isConnecting && !uiState.isConnected && uiState.connectedDevice == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                val waitingText = if (uiState.isServer) "正在广播，等待客户端连接..." else "正在扫描/等待连接..."
                Text(waitingText)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Chat with ${uiState.connectedDevice?.name ?: "..."}") },
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
                val prefix = if (message.startsWith("Me:")) "Me" else uiState.connectedDevice?.name ?: "Them"
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