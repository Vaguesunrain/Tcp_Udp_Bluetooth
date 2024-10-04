package com.ame.tcp_udp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp


class WindowActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ipAddress = intent.getStringExtra("IP_ADDRESS") ?: "" // Get IP address, default to empty string if null
            val parts = ipAddress.split(":")
            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: 0
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(" ip is:$ip  port is:$port") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                    )
                }
            ) { innerPadding ->ChatWindow(
                modifier = Modifier.padding(innerPadding)
            )
                // Screen content goes here, use innerPadding to avoid overlapping with the app bar
            }
        }
    }


}
@Composable
private fun ChatWindow(modifier: Modifier) {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf( "I am server", "I am client")
    val iconitems = listOf(
        Icons.AutoMirrored.Filled.ArrowBack, Icons.AutoMirrored.Filled.ArrowForward,
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
                0 -> TextChatScreen(0) // 将 Modifier 应用于 HomeScreen
                1 -> TextChatScreen(1)
            }
        }
    }
}

@Composable
fun TextChatScreen(UorT: Int) {
    val mydevices = if (UorT == 0) {
        TcpServer()
    } else {TcpClient()
    }

    val downloadIcon = ImageVector.vectorResource(id = R.drawable.download_2_24px)
    val closeIcon = ImageVector.vectorResource(id = R.drawable.close_24px)
    var sendText by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = { /**/ }) {
                    Icon(imageVector = downloadIcon,
                        contentDescription = "Upload File")
                }
                FloatingActionButton(onClick = { /**/ }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "start")
                }
            }
        }
    ) { innerPadding ->
        // Screen content
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TextField(
                    value = sendText,
                    onValueChange = { sendText = it },
                    modifier = Modifier.offset(y = 4.dp, x = (-3).dp),
                    placeholder = { Text("输入待发送数据 ") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {  }

                ) {
                    Text(text = "send")
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Add some spacing above the TextField

        }
    }
}


