package com.example

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localization.LocaleManager
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.StartIoAdManager
import com.example.ui.components.GlassPillButton
import com.example.ui.components.LiquidGlass
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.theme.MythicScriptTheme
import com.startapp.sdk.ads.banner.Banner
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
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

            MythicScriptTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (status) {
                                STATUS_CHECKING -> StatusScreen {
                                    CircularProgressIndicator(color = LiquidGlass.Teal)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        stringResource(R.string.status_checking_connection),
                                        color = LiquidGlass.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                STATUS_OFFLINE -> StatusScreen {
                                    Text(
                                        stringResource(R.string.status_no_internet),
                                        color = LiquidGlass.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    GlassPillButton(
                                        text = stringResource(R.string.action_retry),
                                        onClick = { retryCount++ },
                                    )
                                }

                                else -> MythicScriptApp(
                                    viewModel = viewModel,
                                    onToggleLanguage = {
                                        LocaleManager.toggleLanguage(this@MainActivity)
                                        recreate()
                                    },
                                )
                            }
                        }
                        StartIoBanner()
                    }
                    AdDebugOverlay(modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StartIoAdManager.preload(this)
    }

    override fun onDestroy() {
        // Locale changes recreate the activity; only release the ad flow when really finishing.
        if (isFinishing) {
            StartIoAdManager.release()
        }
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
    LiquidGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(32.dp),
                tint = LiquidGlass.Surface,
                elevation = 16.dp,
                opacity = 0.97f,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun StartIoBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .testTag("startio_banner_container"),
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
