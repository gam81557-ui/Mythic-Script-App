package com.example

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.StartIoAdManager
import com.example.ui.theme.MythicScriptTheme
import com.startapp.sdk.ads.banner.Banner
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StartIoAdManager.init(this)
        StartIoAdManager.preload(this)

        setContent {
            val viewModel: CompanionViewModel = viewModel()
            var status by remember { mutableIntStateOf(STATUS_CHECKING) }
            var retryCount by remember { mutableIntStateOf(0) }

            LaunchedEffect(retryCount) {
                status = STATUS_CHECKING
                delay(800)
                status = if (StartIoAdManager.isOnline(this@MainActivity)) {
                    STATUS_ONLINE
                } else {
                    STATUS_OFFLINE
                }
            }

            MythicScriptTheme(darkTheme = true) {
                when (status) {
                    STATUS_CHECKING -> StatusScreen {
                        CircularProgressIndicator(color = Color(0xFF3B82F6))
                        Spacer(Modifier.height(16.dp))
                        Text("Checking Connection...", color = Color.White)
                    }

                    STATUS_OFFLINE -> StatusScreen {
                        Text("No Internet Connection", color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { retryCount++ }) { Text("Retry") }
                    }

                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            MythicScriptApp(viewModel = viewModel)
                        }
                        StartIoBanner()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        StartIoAdManager.release()
        super.onDestroy()
    }

    private companion object {
        const val STATUS_CHECKING = 0
        const val STATUS_ONLINE = 1
        const val STATUS_OFFLINE = 2
    }
}

private val BannerHeight = 50.dp

@Composable
private fun StatusScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun StartIoBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight),
            factory = { context ->
                FrameLayout(context).apply {
                    addView(
                        Banner(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        },
                    )
                }
            },
        )
    }
}
