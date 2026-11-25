package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localization.LocaleManager
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.UnityAdDebugState
import com.example.ui.UnityAdManager
import com.example.ui.theme.MythicScriptTheme
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UnityAdManager.init(this)
        UnityAdManager.preload(this)

        setContent {
            val viewModel: CompanionViewModel = viewModel()
            var status by remember { mutableIntStateOf(STATUS_CHECKING) }
            var retryCount by remember { mutableIntStateOf(0) }

            LaunchedEffect(retryCount) {
                status = STATUS_CHECKING
                delay(800)
                status = if (UnityAdManager.isOnline(this@MainActivity)) {
                    STATUS_ONLINE
                } else {
                    STATUS_OFFLINE
                }
            }

            MythicScriptTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (status) {
                        STATUS_CHECKING -> StatusScreen {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.status_checking_connection), color = Color.White)
                        }

                        STATUS_OFFLINE -> StatusScreen {
                            Text(stringResource(R.string.status_no_internet), color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { retryCount++ }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }

                        else -> Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                MythicScriptApp(
                                    viewModel = viewModel,
                                    onToggleLanguage = {
                                        LocaleManager.toggleLanguage(this@MainActivity)
                                        recreate()
                                    },
                                )
                            }
                            UnityBanner(this@MainActivity)
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        AdDebugOverlay(modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UnityAdManager.onActivityResumed(this)
    }

    override fun onDestroy() {
        // Locale changes recreate the activity. Keep the Unity session alive across that
        // configuration change; only tear it down when the activity is really finishing.
        if (isFinishing) {
            UnityAdManager.release()
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
private fun UnityBanner(activity: Activity) {
    val initialized by UnityAdManager.isInitialized.collectAsStateWithLifecycle()
    var loadAttempts by remember(activity) { mutableIntStateOf(0) }
    val banner = remember(activity) {
        BannerView(
            activity,
            UnityAdManager.BANNER_PLACEMENT,
            UnityBannerSize(320, 50),
        )
    }

    fun requestBannerLoad() {
        if (loadAttempts >= BANNER_MAX_LOAD_ATTEMPTS) return
        loadAttempts++
        UnityAdManager.onBannerLoading()
        runCatching { banner.load() }
            .onFailure { error ->
                UnityAdManager.onBannerFailed("LOAD_EXCEPTION", error.message.orEmpty())
                if (loadAttempts < BANNER_MAX_LOAD_ATTEMPTS && banner.parent != null) {
                    banner.postDelayed(
                        {
                            if (banner.parent != null) requestBannerLoad()
                        },
                        BANNER_RETRY_DELAY_MS,
                    )
                }
            }
    }

    DisposableEffect(activity) {
        banner.setListener(object : BannerView.IListener {
            override fun onBannerLoaded(banner: BannerView) {
                UnityAdManager.onBannerLoaded()
            }

            override fun onBannerShown(banner: BannerView) {
                UnityAdManager.onBannerShown()
            }

            override fun onBannerClick(banner: BannerView) = Unit

            override fun onBannerFailedToLoad(
                banner: BannerView,
                errorInfo: BannerErrorInfo,
            ) {
                UnityAdManager.onBannerFailed(
                    errorInfo.errorCode.toString(),
                    errorInfo.errorMessage.orEmpty(),
                )
                if (loadAttempts < BANNER_MAX_LOAD_ATTEMPTS && banner.parent != null) {
                    banner.postDelayed(
                        {
                            if (banner.parent != null) requestBannerLoad()
                        },
                        BANNER_RETRY_DELAY_MS,
                    )
                }
            }

            override fun onBannerLeftApplication(banner: BannerView) = Unit
        })

        onDispose {
            runCatching { banner.setListener(null) }
            runCatching { banner.destroy() }
        }
    }

    LaunchedEffect(initialized, activity) {
        if (initialized && loadAttempts == 0) {
            requestBannerLoad()
        }
    }

    // BannerView.load() also initializes the SDK. If the init callback is delayed or lost,
    // this fallback still gives the banner a chance to load instead of staying blank forever.
    LaunchedEffect(activity) {
        delay(BANNER_INIT_FALLBACK_DELAY_MS)
        if (loadAttempts == 0) {
            requestBannerLoad()
        }
    }

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
                        banner,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER,
                        ),
                    )
                }
            },
        )
    }
}

private const val BANNER_MAX_LOAD_ATTEMPTS = 3
private const val BANNER_RETRY_DELAY_MS = 15_000L
private const val BANNER_INIT_FALLBACK_DELAY_MS = 8_000L

@Composable
private fun AdDebugOverlay(modifier: Modifier = Modifier) {
    val state by UnityAdManager.debugState.collectAsStateWithLifecycle()
    AdDebugPanel(state = state, modifier = modifier)
}

@Composable
private fun AdDebugPanel(state: UnityAdDebugState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(6.dp)
            .widthIn(max = 340.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Text(
            text = "AD DEBUG · test mode",
            color = Color(0xFFFBBF24),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        listOf(
            state.init,
            state.initDetail,
            state.net,
            state.banner,
            state.rewarded,
            state.interstitial,
            state.gate,
            "reason=${state.reason}",
        ).filter { it.isNotBlank() }.forEach { line ->
            Text(text = line, color = Color.White, fontSize = 9.sp)
        }
        if (state.lastError.isNotBlank()) {
            Text(
                text = "Last error: ${state.lastError}",
                color = Color(0xFFF87171),
                fontSize = 9.sp,
                maxLines = 4,
            )
        }
        if (state.reason == "INIT_TIMEOUT" || state.reason.startsWith("INIT_FAIL")) {
            TextButton(onClick = { UnityAdManager.retryInit() }) {
                Text(text = "RETRY INIT", color = Color(0xFFFBBF24), fontSize = 9.sp)
            }
        }
    }
}
