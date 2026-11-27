package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.UnityAdGateState

/**
 * Blocking glass overlay shown while the Unity action gate waits for an ad.
 *
 * The dialog cannot be dismissed by the back button or by tapping the scrim, so duplicate
 * Inject/Re-inject/Restore taps cannot start a second action. The countdown disappears as soon
 * as Unity starts showing an ad, or when the gate finishes/fails and the action proceeds.
 */
@Composable
fun AdGateLoadingDialog(state: UnityAdGateState) {
    if (!state.visible) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LiquidGlass.Scrim)
                .testTag("ad_gate_dialog"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(32.dp),
                        tint = LiquidGlass.DialogSurface,
                        elevation = 26.dp,
                        opacity = 0.96f,
                    )
                    .padding(horizontal = 28.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CountdownRing(
                        secondsRemaining = state.secondsRemaining,
                        totalSeconds = state.totalSeconds,
                    )
                    Text(
                        text = stringResource(R.string.ad_gate_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlass.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.ad_gate_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiquidGlass.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(
    secondsRemaining: Int,
    totalSeconds: Int,
) {
    val total = totalSeconds.coerceAtLeast(1)
    val progress = (secondsRemaining.toFloat() / total).coerceIn(0f, 1f)
    val contentDescription = stringResource(
        R.string.ad_gate_countdown_content_description,
        secondsRemaining,
    )

    Box(
        modifier = Modifier
            .size(132.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag("ad_gate_countdown"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            LiquidGlass.Teal.copy(alpha = 0.16f),
                            LiquidGlass.Teal.copy(alpha = 0.04f),
                            LiquidGlass.Glass.copy(alpha = 0.0f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = LiquidGlass.Hairline.copy(alpha = 0.55f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        LiquidGlass.Teal,
                        LiquidGlass.TealDeep,
                        LiquidGlass.Teal,
                    ),
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = secondsRemaining.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displaySmall.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Bold,
            ),
            color = LiquidGlass.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(88.dp)
                .testTag("ad_gate_seconds"),
        )
    }
}
