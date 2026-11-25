package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.UnityAdDebugState
import com.example.ui.UnityAdManager

@Composable
internal fun AdDebugOverlay(modifier: Modifier = Modifier) {
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
            text = "AD DEBUG · debug build",
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
        if (
            state.reason == "INIT_TIMEOUT" ||
            state.reason == "INIT_NO_CALLBACK" ||
            state.reason == "INIT_STUCK_INITIALIZING" ||
            state.reason == "INIT_RETRY_BLOCKED_INITIALIZING" ||
            state.reason.startsWith("INIT_FAIL")
        ) {
            TextButton(onClick = { UnityAdManager.retryInit() }) {
                Text(text = "RETRY INIT", color = Color(0xFFFBBF24), fontSize = 9.sp)
            }
        }
    }
}
