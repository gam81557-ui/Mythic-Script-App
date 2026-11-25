package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Loads remote hero/skin artwork asynchronously from [primaryUrl] and automatically
 * switches to [fallbackUrl] if the primary request fails. Renders a sleek stylized
 * monogram + shield placeholder while loading or when offline/no image URL is present.
 */
@Composable
fun RemoteArtworkImage(
    primaryUrl: String,
    fallbackUrl: String,
    label: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF3B82F6),
) {
    val cleanPrimary = primaryUrl.trim()
    val cleanFallback = fallbackUrl.trim()
    var useFallback by remember(cleanPrimary, cleanFallback) { mutableStateOf(false) }

    val activeUrl = when {
        !useFallback && cleanPrimary.isNotEmpty() -> cleanPrimary
        cleanFallback.isNotEmpty() -> cleanFallback
        else -> ""
    }

    if (activeUrl.isEmpty()) {
        ArtworkFallbackBox(
            label = label,
            accentColor = accentColor,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val request = remember(activeUrl) {
        ImageRequest.Builder(context)
            .data(activeUrl)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = accentColor,
                )
            }
        },
        error = {
            if (!useFallback && cleanFallback.isNotEmpty() && cleanFallback != cleanPrimary) {
                useFallback = true
            }
            ArtworkFallbackBox(
                label = label,
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun ArtworkFallbackBox(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val initials = label.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "ML" }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.34f),
                    Color(0xFFC8CDD6),
                )
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.90f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LiquidGlass.Ink,
            )
        }
    }
}

/**
 * Loads an optional tier/tag badge image from an HTTP(S) URL cleanly at badge height.
 * Renders nothing if blank, invalid, or failed to load (never exposes raw URLs).
 */
@Composable
fun RemoteTagBadgeImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val cleanUrl = url.trim()
    if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
        !cleanUrl.startsWith("https://", ignoreCase = true)
    ) {
        return
    }

    val context = LocalContext.current
    val request = remember(cleanUrl) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(20.dp)
            .widthIn(max = 84.dp),
        error = {},
    )
}
