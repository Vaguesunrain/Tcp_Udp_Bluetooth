package com.ame.tcp_udp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Tcp_UdpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun Greeting( modifier: Modifier = Modifier) {
    val BluetoothVector = ImageVector.vectorResource(id = R.drawable.bluetooth_24px)
    Tcp_UdpTheme {
        var selectedItem by remember { mutableIntStateOf(0) }
        val items = listOf( "TCP", "UDP","BlueTooth")
        val iconitems = listOf(Icons.Filled.Home, Icons.AutoMirrored.Filled.ArrowBack,
            BluetoothVector
        )
        Scaffold(modifier = Modifier.fillMaxSize(),

            bottomBar = {
                NavigationBar {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(iconitems[index], contentDescription = item) },
                            label = { Text(item) },
                            selected = selectedItem == index,
                            onClick = { selectedItem = index }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)) { // 使用Column 包裹内容和底部导航栏
                when (selectedItem) {
                    0 -> TCPScreen() // 将 Modifier 应用于 HomeScreen
                    1 -> UDPScreen()
                    2 -> BLUETOOTHScreen()
                }
            }
        }
    }
}


@Composable
fun UDPScreen() {
   DemoSwipeToDismiss(2)
}


@Composable
fun TCPScreen() {
    DemoSwipeToDismiss(1)
}

@Composable
fun BLUETOOTHScreen(){
    DemoSwipeToDismiss(lable = 3)
}
@Composable
fun NumberIcon(num:Int){
    Box (modifier = Modifier
        .size(50.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)

    ){
        Text(text = num.toString(),
            color = Color.White,
            fontSize = 24.sp,

            modifier = Modifier.align(Alignment.Center))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoSwipeToDismiss(lable: Int,
                       modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialIpList = loadIpListFromSharedPrefs(LocalContext.current)
    var ipList by remember { mutableStateOf(initialIpList) }
    var showDialog by remember { mutableStateOf(false) }
    var ipText by rememberSaveable { mutableStateOf("") }
    val dismissState = remember { mutableStateMapOf<String, SwipeToDismissBoxState>() }
    var isTcpClient by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            // Add your TopAppBar content here
            TopAppBar(
                title = { Text("Swipe to Dismiss Demo") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.offset(y = 4.dp),
                onClick = {showDialog=true/* do something */ },
                containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
            ) {
                Icon(Icons.Filled.Add, "Localized description")
            }
        }
    ) { innerPadding -> // Add inner padding for the content
        LazyColumn(modifier = modifier.padding(innerPadding)) {
            items(ipList.size) { item ->
                val ip = ipList[item]
                Log.i("debug", ipList[item] + "   " + item.toString())
                SwipeBox(
                    swipeState = dismissState.getOrPut(ip) { rememberSwipeToDismissBoxState() },
                    onDelete = {
                        Log.i("debug_", item.toString())
                        ipList = ipList.filter { it != ip }
                        removeIpFromSharedPrefs(ip,context)
                        dismissState.remove(ip)
                    },
                    onEdit = { },
                    //                modifier = Modifier.animateItemPlacement()
                ) {
                    ListItem(
                        headlineContent = { Text(text = ip) },
                        leadingContent = {
                            NumberIcon(num = item)
                        },modifier = Modifier.clickable {
                            val intent = Intent(context, WindowActivity::class.java)
                            intent.putExtra("IP_ADDRESS", ip)
                            context.startActivity(intent)
                        }
                    )}
            }
        }
        if(showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("请按照格式输入并确定 ") },
                text = {
                    Column {
                        TextField(
                            value = ipText,
                            onValueChange = { ipText = it },modifier = Modifier.offset(y = 4.dp, x = (-3).dp),
                            placeholder = { if (!isTcpClient) Text("例如:192.168.43.1:5000 ")
                                            else Text("直接写端口，例如:5000 ")},
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.Center) {
                            Text("使用Server模式")
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = isTcpClient,
                                onCheckedChange = { isTcpClient = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {ipList += ipText
                            showDialog = false
                            saveIpListToSharedPrefs(ipText, context)
                            // 根据 isTcpClient 的值执行不同的操作
                            if (isTcpClient) {
                                // 使用 TcpClient 模式
                            } else {
                                // 使用 TcpServer 模式
                            }
                        }
                    ) {
                        Text("确认")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBox(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    swipeState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState(),
    content: @Composable () -> Unit

) {
    lateinit var icon: ImageVector
    lateinit var alignment: Alignment
    val color: Color

    when (swipeState.dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> {
            icon = Icons.Outlined.Delete
            alignment = Alignment.CenterEnd
            color = MaterialTheme.colorScheme.errorContainer
        }

        SwipeToDismissBoxValue.StartToEnd -> {
            icon = Icons.Outlined.Edit
            alignment = Alignment.CenterStart
            color =
                Color.Green.copy(alpha = 0.3f) // You can generate theme for successContainer in themeBuilder
        }

        SwipeToDismissBoxValue.Settled -> {
            icon = Icons.Outlined.Delete
            alignment = Alignment.CenterEnd
            color = MaterialTheme.colorScheme.errorContainer
        }
    }

    SwipeToDismissBox(
        modifier = modifier.animateContentSize(),
        state = swipeState,
        backgroundContent = {
            Box(
                contentAlignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
            ) {
                Icon(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    imageVector = icon, contentDescription = null
                )
            }
        }
    ) {
        content()
    }

    when (swipeState.currentValue) {
        SwipeToDismissBoxValue.EndToStart -> {
            onDelete()
        }
        SwipeToDismissBoxValue.StartToEnd -> {
            LaunchedEffect(swipeState) {
                onEdit()
                swipeState.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
        SwipeToDismissBoxValue.Settled -> {
        }
    }
}


private fun loadIpListFromSharedPrefs(context: Context): List<String> {
    val sharedPrefs = context.getSharedPreferences("ip_list", Context.MODE_PRIVATE)
    return sharedPrefs.getStringSet("ips", setOf())?.toList() ?: emptyList()
}

private fun saveIpListToSharedPrefs(ipText: String, context: Context) {
    val sharedPrefs = context.getSharedPreferences("ip_list", Context.MODE_PRIVATE)
    val editor = sharedPrefs.edit()
    val ips = sharedPrefs.getStringSet("ips", setOf())?.toMutableSet() ?: mutableSetOf()
    ips.add(ipText)
    editor.putStringSet("ips", ips)
    editor.apply()
}
private fun removeIpFromSharedPrefs(ipText: String, context: Context) {
    val sharedPrefs = context.getSharedPreferences("ip_list", Context.MODE_PRIVATE)
    val editor = sharedPrefs.edit()
    val ips= sharedPrefs.getStringSet("ips", setOf())?.toMutableSet() ?: mutableSetOf()
    ips.remove(ipText)
    editor.putStringSet("ips", ips)
    editor.apply()
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoSwipeToDismiss(1)

}