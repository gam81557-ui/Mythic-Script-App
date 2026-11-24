package com.example
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.startapp.sdk.adsbase.*
import com.startapp.sdk.ads.banner.Banner
import kotlinx.coroutines.delay
class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState); enableEdgeToEdge(); StartAppSDK.init(this, "210526324", false); StartAppAd.disableSplash(); setContent {
val vm: CompanionViewModel = viewModel(); var st by remember { mutableStateOf(0) }; var tc by remember { mutableStateOf(0) }; LaunchedEffect(tc) { st = 0; delay(800); st = if (StartIoAdManager.isOnline(this@MainActivity)) 1 else 2 };
when (st) {
0 -> Box(Modifier.fillMaxSize().background(Color(0xFF0F0F14)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color(0xFF3B82F6)); Spacer(Modifier.height(16.dp)); Text("Checking Connection...", color = Color.White) } }
2 -> Box(Modifier.fillMaxSize().background(Color(0xFF0F0F14)), contentAlignment = Alignment.CenterHorizontally) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("No Internet Connection", color = Color.White); Spacer(Modifier.height(16.dp)); Button(onClick = { tc++ }) { Text("Retry") } } }
else -> Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { MythicScriptApp(viewModel = vm) }; val b = remember { Banner(this@MainActivity) }; AndroidView(factory = { b }, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) }
} } } }