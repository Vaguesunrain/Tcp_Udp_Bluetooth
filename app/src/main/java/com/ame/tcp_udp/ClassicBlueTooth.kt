@file:Suppress("UnusedImport", "DEPRECATION") // Suppressing DEPRECATION for older API support
package com.ame.tcp_udp

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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class ClassicBlueTooth : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No need for onActivityResult anymore. All logic is in the composable.
        setContent {
            Tcp_UdpTheme {
                ClassicBluetoothScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ClassicBluetoothScreen() {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter: BluetoothAdapter? = remember { bluetoothManager.adapter }

    // State for all our data
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var discoveredDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(false) }
    // State to track if bluetooth is enabled, this will drive UI changes
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    // Activity Result Launcher for enabling Bluetooth
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

    // Function to update the list of paired devices
    val updatePairedDevices = {
        if (bluetoothAdapter?.isEnabled == true) {
            pairedDevices = bluetoothAdapter.bondedDevices.toList()
        } else {
            pairedDevices = emptyList()
        }
    }

    // Permissions logic
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    // Effect to update paired devices list when permissions are granted or BT is enabled
    LaunchedEffect(key1 = permissionState.allPermissionsGranted, key2 = isBluetoothEnabled) {
        if (permissionState.allPermissionsGranted && isBluetoothEnabled) {
            updatePairedDevices()
        }
    }

    // Central BroadcastReceiver for all Bluetooth actions
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    // --- Handles Manual On/Off of Bluetooth ---
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_ON) {
                            isBluetoothEnabled = true
                            updatePairedDevices() // Refresh list when turned on
                        } else if (state == BluetoothAdapter.STATE_OFF) {
                            isBluetoothEnabled = false
                            // Clear all lists
                            pairedDevices = emptyList()
                            discoveredDevices = emptyList()
                            isDiscovering = false
                        }
                    }
                    // --- Handles Discovery ---
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> isDiscovering = true
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> isDiscovering = false
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            if (it.name != null && it.address !in discoveredDevices.map { d -> d.address }) {
                                discoveredDevices = discoveredDevices + it
                            }
                        }
                    }
                    // --- Handles Pairing (Bonding) ---
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> {
                                Toast.makeText(context, "${device?.name} successfully paired!", Toast.LENGTH_SHORT).show()
                                // Refresh UI: Add to paired list and remove from discovered list
                                updatePairedDevices()
                                discoveredDevices = discoveredDevices.filter { it.address != device?.address }
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Toast.makeText(context, "Pairing with ${device?.name}...", Toast.LENGTH_SHORT).show()
                            }
                            BluetoothDevice.BOND_NONE -> {
                                Toast.makeText(context, "Pairing with ${device?.name} failed or was canceled.", Toast.LENGTH_SHORT).show()
                            }
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
            context.unregisterReceiver(receiver)
            // Ensure discovery is cancelled when the composable is disposed
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
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
                // Permissions UI
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
                // Bluetooth Disabled UI
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
                // Main UI when everything is enabled
                MainBluetoothContent(
                    pairedDevices = pairedDevices,
                    discoveredDevices = discoveredDevices,
                    isDiscovering = isDiscovering,
                    onStartDiscovery = {
                        discoveredDevices = emptyList() // Clear previous results
                        bluetoothAdapter.startDiscovery()
                    },
                    onStopDiscovery = { bluetoothAdapter.cancelDiscovery() },
                    onPairDevice = { device ->
                        // Cancel discovery before pairing for better performance
                        if (isDiscovering) {
                            bluetoothAdapter.cancelDiscovery()
                        }
                        device.createBond()
                    }
                )
            }
        }
    }
}

@Composable
fun MainBluetoothContent(
    pairedDevices: List<BluetoothDevice>,
    discoveredDevices: List<BluetoothDevice>,
    isDiscovering: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onPairDevice: (BluetoothDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Paired Devices Section
        Text("Paired Devices", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        PairedDevicesList(
            devices = pairedDevices,
            onDeviceClick = { /* Handle click on paired device if needed */ }
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Discovery Section
        Text("Discover Devices", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = if (isDiscovering) onStopDiscovery else onStartDiscovery) {
            Text(if (isDiscovering) "Stop Discovery" else "Start Discovery")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isDiscovering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scanning...")
            }
        }

        DiscoveredDevicesList(
            devices = discoveredDevices,
            onDeviceClick = onPairDevice // Pass the pairing function here
        )
    }
}

// Renamed for clarity
@Composable
fun DiscoveredDevicesList(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    if (devices.isEmpty() && devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new devices found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = devices, key = { it.address }) { device ->
                DeviceItem(device = device, onDeviceClick = onDeviceClick)
                Divider()
            }
        }
    }
}

@Composable
fun PairedDevicesList(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    if (devices.isEmpty()) {
        Text("No paired devices.")
    } else {
        LazyColumn(modifier = Modifier.height(150.dp)) { // Give it a fixed height or weight in a Column
            items(items = devices, key = { it.address }) { device ->
                DeviceItem(
                    device = device,
                    onDeviceClick = onDeviceClick
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
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceClick(device) }
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