package com.ame.tcp_udp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
    Tcp_UdpTheme {
        var selectedItem by remember { mutableIntStateOf(0) }
        val items = listOf( "TCP", "UDP","BlueTooth")
        val iconitems = listOf(Icons.Filled.Home, Icons.AutoMirrored.Filled.ArrowBack,
            Icons.AutoMirrored.Filled.ArrowForward
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
                    0 -> HomeScreen(Modifier.fillMaxSize()) // 将 Modifier 应用于 HomeScreen
                    1 -> TCPScreen()
                    2 -> UDPScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier) {
    var ipList by remember { mutableStateOf(listOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var ipText by rememberSaveable { mutableStateOf("") }
    val checkedStates = remember { mutableStateMapOf<String, Boolean>() }
    val dismissState = remember { mutableStateMapOf<String, SwipeToDismissBoxState>() }
    Scaffold(
        floatingActionButton = {
            Row {
                TextField(
                    value = ipText,
                    onValueChange = {ipText = it },
                    modifier = Modifier.offset(y = 4.dp,x= (-3).dp),
                    placeholder = { Text("Input the ip") },

                    )
                Spacer(modifier = Modifier.width(16.dp))
                FloatingActionButton(
                    modifier = Modifier.offset(y = 4.dp),
                    onClick = {showDialog=true/* do something */ },
                    containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                ) {
                    Icon(Icons.Filled.Add, "Localized description")
                }
            }
        }

    ) {padding->
        LazyColumn(
            contentPadding = padding,
            ) {
            items(ipList.size) { index ->
                val ip = ipList[index]
                val stateip = dismissState.getOrPut(ip){ rememberSwipeToDismissBoxState()}
                    SwipeToDismissBox(
                        state =stateip ,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState[ip]?.dismissDirection) {
                                    SwipeToDismissBoxValue.EndToStart -> Color.Red
                                    else -> Color.LightGray
                                },
                                finishedListener = {
                                    targetValue ->
                                    when(targetValue){
                                        Color.Red -> {
                                            ipList = ipList.filter { it != ip }
                                            dismissState.remove(ip)
                                        }
                                        else -> {}
                                    }
                                }
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(color))
                        }
                    ) {
                        Card(Modifier.size(width = Int.MAX_VALUE.dp, height = 100.dp),
                            shape = RoundedCornerShape(0)
                        ) {
                            Row (
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NumberIcon(num = index)
                                Text(text = ipList[index] ,style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start =20.dp, end = 100.dp)
                                )
                                Spacer(modifier = modifier.width(80.dp))
                                Checkbox(
                                    checked = checkedStates.getOrDefault(ip, false),
                                    onCheckedChange = { isChecked ->
                                        checkedStates[ip] = isChecked
                                    }
                                )

                            }
                            Box(Modifier.fillMaxSize()) { Text(" ", Modifier.align(Alignment.Center)) }
                        }
                    }
                when(dismissState[ip]?.dismissDirection){
                    SwipeToDismissBoxValue.EndToStart -> {

                        ipList = ipList.filter { it != ip }
                        dismissState.remove(ip)
                    }
                    else -> {}
                }
            }
        }
        if(showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("确认添加") },
                text = { Text("是否添加IP地址 ？${ipText}") },
                confirmButton = {
                    Button(
                        onClick = {
                            ipList += ipText
                            showDialog = false
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
    Text(text = "Home")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UDPScreen() {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by
            animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.LightGray
                    SwipeToDismissBoxValue.StartToEnd -> Color.Green
                    SwipeToDismissBoxValue.EndToStart -> Color.Red
                }, label = ""
            )
            println(dismissState.targetValue)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color))
        }

    ) {
        OutlinedCard(shape = RectangleShape) {
            ListItem(
                headlineContent = { Text("Cupcake") },
                supportingContent = { Text("Swipe me left or right!") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TCPScreen() {
    Text(text = "dafafaf")
    DemoSwipeToDismiss()
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
fun DemoSwipeToDismiss(
    modifier: Modifier = Modifier
) {

    var ipList by remember { mutableStateOf(listOf<String>("123313","131313451","13131414","adwdad","edqadada")) }
    val dismissState = remember { mutableStateMapOf<String, SwipeToDismissBoxState>() }
    LazyColumn(modifier = modifier) {
        items(ipList.size) { item ->
            val ip = ipList[item]
            Log.i("debug",ipList[item]+"   "+item.toString())
            SwipeBox(
                swipeState = dismissState.getOrPut(ip){ rememberSwipeToDismissBoxState()},
                onDelete = {
                    Log.i("debug_", item.toString())
                    ipList = ipList.filter { it != ip }
                },
                onEdit = { },
//                modifier = Modifier.animateItemPlacement()
            ) {

                ListItem(headlineContent = { Text(text = "Headline text $ip") },
                    supportingContent = { Text(text = "Supporting text $ip") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.AccountBox,
                            contentDescription = null
                        )
                    }
                )
            }
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoSwipeToDismiss()

}