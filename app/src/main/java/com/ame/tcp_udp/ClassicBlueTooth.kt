@file:Suppress("UnusedImport", "DEPRECATION") // Suppressing DEPRECATION for older API support
package com.ame.tcp_udp

import com.ame.tcp_udp.BlueTooth.BluetoothViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.ame.tcp_udp.BlueTooth.BluetoothUiState
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class ClassicBlueTooth : ComponentActivity() {

    // Use the viewModels delegate to get the ViewModel
    private val viewModel: BluetoothViewModel by lazy {
        BluetoothViewModel.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Tcp_UdpTheme {
                // 当ViewModel的uiState变化时，UI会自动重组
                val uiState by viewModel.uiState.collectAsState()
                ClassicBluetoothScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    // 新增一个回调，用于启动服务器模式
                    onStartServerMode = {
                        val intent = Intent(this, BluetoothChatActivity::class.java).apply {
                            // 传入一个标志，告诉下一个Activity要以服务器模式启动
                            putExtra("START_MODE", "SERVER")
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
fun ClassicBluetoothScreen(
    viewModel: BluetoothViewModel,
    uiState: BluetoothUiState,
    onStartServerMode: () -> Unit // 新增的回调
) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter: BluetoothAdapter? = remember { bluetoothManager.adapter }

    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isBluetoothEnabled = true
            viewModel.updatePairedDevices()
            Toast.makeText(context, "Bluetooth enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth enabling was cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // NEW: Launcher for making the device discoverable
    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(context, "Device is discoverable for 30 seconds", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Request to be discoverable was denied.", Toast.LENGTH_SHORT).show()
        }
    }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 对于 Android 12+，经典蓝牙也需要 SCAN 和 CONNECT 权限
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT ,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE // Making device discoverable requires this
        )
    } else {
        // 对于 Android 11 及以下，需要旧的权限和位置权限
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION // 经典蓝牙扫描需要位置权限
        )
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    LaunchedEffect(key1 = permissionState.allPermissionsGranted, key2 = isBluetoothEnabled) {
        if (permissionState.allPermissionsGranted && isBluetoothEnabled) {
            viewModel.updatePairedDevices()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_ON) {
                            isBluetoothEnabled = true
                            viewModel.updatePairedDevices()
                        } else if (state == BluetoothAdapter.STATE_OFF) {
                            isBluetoothEnabled = false
                            viewModel.disconnect()
                            viewModel.updatePairedDevices()
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> viewModel.onDiscoveryStarted()
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> viewModel.onDiscoveryFinished()
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { viewModel.onDeviceFound(it) }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        viewModel.onBondStateChanged()
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> Toast.makeText(context, "${device?.name} successfully paired!", Toast.LENGTH_SHORT).show()
                            BluetoothDevice.BOND_BONDING -> Toast.makeText(context, "Pairing with ${device?.name}...", Toast.LENGTH_SHORT).show()
                            BluetoothDevice.BOND_NONE -> Toast.makeText(context, "Pairing with ${device?.name} failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            Log.d("BluetoothDispose", "DisposableEffect for BroadcastReceiver is disposing.")
            context.unregisterReceiver(receiver)
            Log.d("BluetoothDispose", "Calling viewModel.stopDiscovery() from onDispose.")
            viewModel.stopDiscovery()
        }
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
                // ... (Permission UI remains the same)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Bluetooth features require permissions. Please grant the required permissions.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text("Request Permissions")
                    }
                }
            } else if (!isBluetoothEnabled) {
                // ... (Bluetooth Disabled UI remains the same)
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
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Button(onClick = onStartServerMode) {
                        Text("Open Server (Wait for connecting)")
                    }
                    Spacer(Modifier.height(16.dp))

                    Spacer(Modifier.height(16.dp))

                    MainBluetoothContent(
                        uiState = uiState,
                        onStartDiscovery = viewModel::startDiscovery,
                        onStopDiscovery = viewModel::stopDiscovery,
                        onPairDevice = viewModel::pairDevice,
                        // 点击配对列表的设备时，仍然是尝试连接
                        onConnectDevice = viewModel::connectToDevice,
                        // NEW: Pass the lambda to make the device discoverable
                        onMakeDiscoverable = {
                            // This intent prompts the user to make their device discoverable
                            val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                // The discoverable duration is 30 seconds. The maximum is 300 seconds.
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30)
                            }
                            discoverableLauncher.launch(discoverableIntent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainBluetoothContent(
    uiState: BluetoothUiState,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onPairDevice: (BluetoothDevice) -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onMakeDiscoverable: () -> Unit // NEW parameter
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Paired Devices", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        PairedDevicesList(
            devices = uiState.pairedDevices,
            connectedDeviceName = uiState.connectedDeviceName,
            isConnecting = uiState.isConnecting,
            onDeviceClick = onConnectDevice
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp), thickness = 2.dp)

        Text("Discover Devices", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // MODIFIED: Use a Row for the buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = if (uiState.isDiscovering) onStopDiscovery else onStartDiscovery) {
                Text(if (uiState.isDiscovering) "Stop Discovery" else "Start Discovery")
            }
            Spacer(modifier = Modifier.width(16.dp))
            // NEW: The discoverable button
            Button(onClick = onMakeDiscoverable) {
                Text("Make Discoverable (30s)")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.isDiscovering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scanning...")
            }
        }
        else{
            Text(" ")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card (Modifier.fillMaxHeight(0.5f)){

            DiscoveredDevicesList(
                devices = uiState.discoveredDevices,
                onDeviceClick = onPairDevice
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        val scrollState = rememberScrollState()
        Card (Modifier.fillMaxSize().verticalScroll(scrollState),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent // 设置为透明
            )){
            Text(text = "Readme(You are supposed to read 1-4.):\n\n        1.Find each other:one clicks Start Discovery and another clicks Make Discoverable.\n        2.Click name in Discover Devices to pair.\n       3.CLick name in Pair Devices to try connect.The other app should open Server\n       4.Both modes release sources when disconnect.")
        }
    }
}

@Composable
fun DiscoveredDevicesList(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    if (devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new devices found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = devices, key = { it.address }) { device ->
                DeviceItem(device = device, onDeviceClick = { onDeviceClick(device) })
                Divider()
            }
        }
    }
}

@Composable
fun PairedDevicesList(
    devices: List<BluetoothDevice>,
    connectedDeviceName: String?,
    isConnecting: Boolean,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    if (devices.isEmpty()) {
        Text("没有已配对的设备。")
    } else {
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(items = devices, key = { it.address }) { device ->
                val isConnected = device.name == connectedDeviceName
                DeviceItem(
                    device = device,
                    isConnected = isConnected,
                    isConnecting = isConnecting && device.name == connectedDeviceName,
                    onDeviceClick = {
                        // 只有在未连接时，点击才有效
                        if (!isConnected) {
                            onDeviceClick(device)
                        }
                    },
                    // 当连接成功后，"Chat" 按钮会导航到聊天界面
                    onChatClick = {
                        val intent = Intent(context, BluetoothChatActivity::class.java).apply{
                            // 这里我们不传递 "START_MODE"，因为我们是作为客户端进入的
                        }
                        context.startActivity(intent)
                    }
                )
                Divider()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnected: Boolean = false,
    isConnecting: Boolean = false,
    onDeviceClick: () -> Unit,
    onChatClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
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
            if (isConnecting) {
                Text(text = "Connecting...", color = MaterialTheme.colorScheme.primary)
            } else if (isConnected) {
                Text(text = "Connected", color = Color(0xFF008000)) // A nice green color
            }
        }
        if (isConnected && onChatClick != null) {
            Button(onClick = onChatClick) {
                Text("Chat")
            }
        }
    }
}