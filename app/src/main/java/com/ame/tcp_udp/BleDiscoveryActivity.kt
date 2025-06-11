// BleDiscoveryActivity.kt

@file:Suppress("UnusedImport", "DEPRECATION")
package com.ame.tcp_udp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ame.tcp_udp.BlueTooth.BleUiState
import com.ame.tcp_udp.BlueTooth.BleViewModel
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class BleDiscoveryActivity : ComponentActivity() {

    private val viewModel: BleViewModel by lazy {
        BleViewModel.getInstance(application)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Tcp_UdpTheme {
                val uiState by viewModel.uiState.collectAsState()

                BleDiscoveryScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onStartServerMode = {
                        // 启动服务器模式（广播），并导航到聊天界面等待连接
                        val intent = Intent(this, BleChatActivity::class.java).apply {
                            putExtra("START_MODE", "SERVER")
                        }
                        startActivity(intent)
                    },
                    onConnectToDevice = { device ->
                        viewModel.connectToDevice(device)
                    } ,
                    onGoToChat = {
                        val intent = Intent(this, BleChatActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // 这里的逻辑是：如果当前没有活动连接，并且没有正在扫描，
        // 那么我们认为列表可能是过时的，需要清空。
        val currentState = viewModel.uiState.value
        if (!currentState.isConnected && !currentState.isConnecting && !currentState.isScanning) {
            // 从聊天界面返回时，通常满足这些条件。
            // 这将确保用户每次回到发现页时，看到的都是一个干净的界面，
            // 鼓励他们重新扫描以获取最新的设备列表。
            viewModel.clearScannedDevices()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun BleDiscoveryScreen(
    viewModel: BleViewModel,
    uiState: BleUiState,
    onStartServerMode: () -> Unit,
    onConnectToDevice: (BluetoothDevice) -> Unit,
    onGoToChat: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter: BluetoothAdapter? = remember { bluetoothManager.adapter }

    // 检查蓝牙是否开启的状态
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isBluetoothEnabled = true
            Toast.makeText(context, "Bluetooth enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth enabling was cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // BLE需要的新权限
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION // 旧版本扫描需要位置权限
        )
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    // 不再需要复杂的 BroadcastReceiver，ViewModel和其内部的Manager会处理一切
    // 只在权限或蓝牙状态变化时做简单的UI更新
    LaunchedEffect(permissionState.allPermissionsGranted, isBluetoothEnabled) {
        // 可以在这里触发一些初始操作，如果需要的话
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bluetoothAdapter == null) {
                Text("This device does not support Bluetooth")
                return@Scaffold
            }

            if (!permissionState.allPermissionsGranted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "BLE features require permissions. Please grant the required permissions.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text("Request Permissions")
                    }
                }
            }
            else if (!isBluetoothEnabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bluetooth is currently disabled.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        enableBluetoothLauncher.launch(enableBtIntent)
                    }) {
                        Text("Enable Bluetooth")
                    }
                }
            }
            else {
                BleMainContent(
                    uiState = uiState,
                    onStartScan = viewModel::startClientMode,
                    onStopScan = viewModel::stopScan,
                    onConnectDevice = onConnectToDevice, // 【传递】
                    onStartServerMode = onStartServerMode,
                    onGoToChat = onGoToChat // 【传递】
                )
            }
        }
    }
}

@Composable
fun BleMainContent(
    uiState: BleUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onStartServerMode: () -> Unit,
    onGoToChat: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 成为服务器（外围设备）的按钮
        Button(onClick = onStartServerMode) {
            Text("Become discoverable (Server Mode)")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp), thickness = 2.dp)

        // 2. 扫描和连接（中心设备）区域
        Text("Scan for Devices (Client Mode)", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = if (uiState.isScanning) onStopScan else onStartScan) {
            Text(if (uiState.isScanning) "Stop Scan" else "Start Scan")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scanning for devices with our service...")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 显示扫描到的设备列表
        ScannedDevicesList(
            uiState = uiState,
            onDeviceClick = onConnectDevice,
            onChatClick = onGoToChat
        )
    }
}

@Composable
fun ScannedDevicesList(
    uiState: BleUiState,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onChatClick: () -> Unit
) {
    Card(Modifier.fillMaxHeight(0.5f)){
        if (uiState.scannedDevices.isEmpty() && !uiState.isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No devices found. Make sure the server is advertising.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = uiState.scannedDevices, key = { it.address }) { device ->
                    val isConnecting = uiState.connectingDevice?.address == device.address
                    val isConnected = uiState.connectedDevice?.address == device.address

                    BleDeviceItem(
                        device = device,
                        isConnecting = isConnecting,
                        isConnected = isConnected,
                        onConnectClick = {
                            if (!isConnected && !isConnecting) {
                                onDeviceClick(device)
                            }
                        },
                        onChatClick = if (isConnected) onChatClick else null
                    )
                    Divider()
                }
            }
        }
    }
    Spacer(Modifier.height(40.dp))
    val scrollState = rememberScrollState()
    Card (
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // 设置为透明
        )){
        Text(text = "Readme(You are supposed to read 1-5.):\n\n        1.Server mode -> gatt server.\n\n        2.If server is disconnected and closed,this app's client don't know. I let server send 'DISCONNECT' before it closes so that client can react and close.\n\n       3.The Client shut down the server but it knows,so it  send  nothing.\n\n        4.How to use:Server make itself discoverable,client scans.Client clicks device and wait showing connected.\n\n       5.Both modes release sources when disconnect.")
    }
}
@SuppressLint("MissingPermission")
@Composable
fun BleDeviceItem(
    device: BluetoothDevice,
    isConnecting: Boolean,
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onChatClick: (() -> Unit)? // 回调可以是null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnectClick) // 点击行以连接
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween // 左右对齐
    ) {
        Column(modifier = Modifier.weight(1f)) { // 让文字部分占据可用空间
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium
            )
            if (isConnecting) {
                Text(text = "Connecting...", color = MaterialTheme.colorScheme.primary)
            } else if (isConnected) {
                Text(text = "Connected", color = Color(0xFF008000)) // 绿色
            }
        }
        // 如果已连接并且有回调，则显示 "Chat" 按钮
        if (isConnected && onChatClick != null) {
            Button(onClick = onChatClick) {
                Text("Chat")
            }
        }
    }
}