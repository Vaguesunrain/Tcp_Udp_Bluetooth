package com.ame.tcp_udp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting( modifier: Modifier = Modifier) {
    Tcp_UdpTheme {
        var selectedItem by remember { mutableIntStateOf(0) }
        val items = listOf("列表", "TCP", "UDP")
        val iconitems = listOf(Icons.Filled.Menu, Icons.AutoMirrored.Filled.ArrowBack,
            Icons.AutoMirrored.Filled.ArrowForward
        )
        var Ip_list by remember { mutableStateOf(listOf<String>()) }
        var showDialog by remember { mutableStateOf(false) }
        var ipText by rememberSaveable { mutableStateOf("") }

        Scaffold(modifier = Modifier.fillMaxSize(),
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
            },
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
            val checkedState = remember { mutableStateOf(true) }
            LazyColumn(
                contentPadding = innerPadding,

            ) {
                items(Ip_list.size) { index ->
                    Card(Modifier.size(width = Int.MAX_VALUE.dp, height = 100.dp),
                        shape = RoundedCornerShape(0)
                        ) {

                        Row (
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NumberIcon(num = index)
                            Text(text = Ip_list[index] ,style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start =20.dp, end = 100.dp)
                            )
                            Spacer(modifier = modifier.width(100.dp))
                            Checkbox(
                                checked = checkedState.value, onCheckedChange = { checkedState.value = it }
                            )

                        }
                        Box(Modifier.fillMaxSize()) { Text(" ", Modifier.align(Alignment.Center)) }
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
                                Ip_list += ipText
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
    }
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



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
NumberIcon(num = 1)

}