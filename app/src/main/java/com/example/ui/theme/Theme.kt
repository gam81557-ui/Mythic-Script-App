package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MythicDarkScheme = darkColorScheme(
    primary = Color(0xFFF0B90B),
    onPrimary = Color(0xFF0A0E17),
    primaryContainer = Color(0xFF2A2111),
    onPrimaryContainer = Color(0xFFFDD2A),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF071318),
    secondaryContainer = Color(0xFF0E3142),
    onSecondaryContainer = Color(0xFFC1F6FF),
    background = Color(0xFF0B0F19),
    onBackground = Color(0xFFF2F6FC),
    surface = Color(0xFF131929),
    onSurface = Color(0xFFF2F6FC,
    surfaceVariant = Color(0xFF1C2438),
    onSurfaceVariant = Color(0xFFA0B0C8),
    outline = Color(0xFF3A4666),
    error = Color(0xFFFF5252))

@Composable
fun MythicScriptTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MythicDarkScheme,
        content = content
    )
}
