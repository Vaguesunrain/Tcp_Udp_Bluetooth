package com.ame.tcp_udp
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ame.tcp_udp.ui.theme.Tcp_UdpTheme
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*



class WindowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var SerOrClient by remember { mutableIntStateOf(0) }
            var port by remember { mutableIntStateOf(0) }
            var ip by remember { mutableStateOf("") }
            val messageIp = intent.getStringExtra("IP_ADDRESS") ?: "" // Get IP address, default to empty string if null
            if (messageIp.isNotEmpty()) {
                if (messageIp[0] == 'S') {
                    SerOrClient = 1
                    port=messageIp.substring(8).toInt()
                    ip = getLocalIpAddress(LocalContext.current)!!

                } else if (messageIp[0] == 'C') {
                    SerOrClient = 0
                    ip = messageIp.substring(8)
                    port = ip.substring(ip.indexOf(':') + 1).toInt()
                    ip = ip.substring(0, ip.indexOf(':'))
                }
            }
            Tcp_UdpTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.statusBars) { innerPadding ->
                    workUI(
                        modifier = Modifier.padding(innerPadding),SerOrClient,ip,port
                    )
                }
            }
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun workUI( modifier: Modifier = Modifier ,SerOrClient: Int , theIp: String ,thePort: Int) {
//    注意SerOrClient=1时是服务器
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (SerOrClient == 1) {
                        Text(text = "Now work on TCP Server " , fontSize = 25.sp)
                    } else {
                        Text(text = "Now work on TCP Client",fontSize = 25.sp)
                    }
                },
                modifier = Modifier.height(100.dp)

            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding) // This ensures content is below topBar and above navBar
                .fillMaxSize() ,// It's often good to fill the available space
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var sendMassage by remember { mutableStateOf("") }
            val messageslist = listOf(
                "Hello",
                "How are you?",
                "This is a longer message to demonstrate scrolling.",
                "Another message here.",
                "And one more for good measure.",
                "Testing the maximum height.",
                "Still testing...",
                "Reached the limit?"
            )
            Row {
                var connectState by remember { mutableStateOf(false) }
                if (SerOrClient == 1) {
                    Column {
                        Text(text = "Local IP is $theIp ")
                        Text(text = "Port is $thePort")
                    }
                } else {
                    Column {
                        Text(text = "Local IP is $theIp ")
                        Text(text = "Port is $thePort")
                    }
                }
                Spacer(modifier.width(70.dp))
                Button(onClick = {
                    connectState = !connectState
                }) {
                    Text(
                        if (connectState)
                            "Disconnect"
                        else
                            "Connect"
                    )
                }
            }
            Spacer(modifier.heightIn(10.dp))
            MessageListDisplay(messageslist )
            Spacer(modifier.heightIn(5.dp))
            TextField(
//                modifier = Modifier.fillMaxWidth(0.8f),
                value = sendMassage,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done // 将回车键设置为“完成”
                ),
                onValueChange = { newValue -> sendMassage = newValue },modifier = Modifier.offset(y = 4.dp, x = (-3).dp).fillMaxWidth(0.8f),
                placeholder = {  Text("Please enter message")},
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row() {
                Button(modifier= Modifier
                    .width(120.dp)
                    .height(50.dp), onClick = { /*TODO*/ }) {
                    Text(text = "Send")
                }
                Spacer(modifier = Modifier.padding(20.dp))
                Button(modifier= Modifier
                    .width(120.dp)
                    .height(50.dp), onClick = { /*TODO*/ }) {
                    Text(text = "Save text")
                }
            }
            
        }
    }
}

@Composable
fun MessageListDisplay(messageList: List<String>) {
    LazyColumn(
        modifier = Modifier
//            .heightIn(max = 400.dp) // Set the maximum height
//            .background(Color.LightGray) // Set the background color
            .fillMaxWidth(0.8f)
            .fillMaxHeight(0.7f)
    ) {
        items(messageList) { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth() // Make each Text take the full width of the LazyColumn
                    .padding(3.dp) // Add some padding around each message
            )
        }
    }
}


fun getLocalIpAddress(context: Context): String? {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val currentNetwork = connectivityManager.activeNetwork
        ?: return null // No active network

    val networkCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork) ?: return null
    Log.i("hihi","1")
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        // Connected to Wi-Fi, try to get Wi-Fi IP
        Log.i("hihi","20")
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Log.i("hihi","20")
        val wifiInfo = wifiManager.connectionInfo
        val ipAddressInt = wifiInfo.ipAddress
        Log.i("hihi","2")
        if (ipAddressInt != 0) {
            Log.i("hihi","3")
            return String.format(
                Locale.getDefault(),
                "%d.%d.%d.%d",
                ipAddressInt and 0xff,
                ipAddressInt shr 8 and 0xff,
                ipAddressInt shr 16 and 0xff,
                ipAddressInt shr 24 and 0xff
            )
        }
    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        Log.i("hihi","4")
        // Connected to mobile data, iterate through network interfaces
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IP Address", "Error getting mobile IP address", e)
        }
    }
    Log.i("hihi", "Errmobile IP address")
    // Fallback: Iterate through all network interfaces
    try {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            val inetAddresses = networkInterface.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        Log.e("IP Address", "Error getting IP address", e)
    }

    return null
}

