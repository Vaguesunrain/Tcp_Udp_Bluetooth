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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ame.tcp_udp.BlueTooth.BleUiState
import com.ame.tcp_udp.BlueTooth.BleViewModel
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class BleDiscoveryActivity : ComponentActivity() {

    // 使用我们为BLE创建的ViewModel单例
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
                        // 连接到选定的设备，并导航到聊天界面
                        viewModel.connectToDevice(device)
                        val intent = Intent(this, BleChatActivity::class.java).apply {
                            // 客户端模式不需要传递额外参数
                        }
                        startActivity(intent)
                    }
                )
            }
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
    onConnectToDevice: (BluetoothDevice) -> Unit
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

            // 权限请求UI (基本不变)
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
            // 蓝牙开启请求UI (基本不变)
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
            // 主内容
            else {
                BleMainContent(
                    uiState = uiState,
                    onStartScan = viewModel::startClientMode, // 注意：这里调用的是startClientMode
                    onStopScan = viewModel::disconnect, // 断开连接也可以用来停止扫描
                    onConnectDevice = onConnectToDevice,
                    onStartServerMode = onStartServerMode
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
    onStartServerMode: () -> Unit
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
            devices = uiState.scannedDevices,
            onDeviceClick = onConnectDevice
        )
    }
}

@Composable
fun ScannedDevicesList(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    if (devices.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No devices found. Make sure the server is advertising.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = devices, key = { it.address }) { device ->
                // 使用我们之前为经典蓝牙写的DeviceItem，它足够通用
                BleDeviceItem(device = device, onDeviceClick = { onDeviceClick(device) })
                Divider()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BleDeviceItem(
    device: BluetoothDevice,
    onDeviceClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}