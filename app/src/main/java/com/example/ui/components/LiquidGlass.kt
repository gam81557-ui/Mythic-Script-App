package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * iOS 26 inspired Liquid Glass palette. The glass surfaces deliberately use fixed cool
 * neutrals rather than the Material scheme so the refraction and rim light stay consistent
 * across light and dark system settings.
 */
object LiquidGlass {
    val BackgroundTop = Color(0xFFB8BCC6)
    val BackgroundBottom = Color(0xFFC8CDD6)
    val Ink = Color(0xFF111827)
    val Muted = Color(0xFF374151)
    val Teal = Color(0xFF00B4B8)
    val TealDeep = Color(0xFF006E72)
    val Red = Color(0xFFFF1E1E)
    val RedDeep = Color(0xFFC1121F)
    val Glass = Color(0xFFFFFFFF)
    val GlassTint = Color(0xFFF3F5F9)
    val Surface = Color(0xFFF2F4F8)
    val SurfaceStrong = Color(0xFFF7F8FB)
    val SurfaceMuted = Color(0xFFE7EBF1)
    val SheetSurface = Color(0xFFF4F6FA)
    val DialogSurface = Color(0xFFF5F7FB)
    val SelectedSurface = Color(0xFFE2F7F7)
    val ActiveSurface = Color(0xFFE4F8EC)
    val WarningSurface = Color(0xFFFFF1D6)
    val DangerSurface = Color(0xFFFFE4E4)
    val Scrim = Color(0x66070B13)
    val Hairline = Color(0xFFC9D0DA)
    val Warning = Color(0xFFB87500)
}

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        LiquidGlass.BackgroundTop,
                        Color(0xFFC0C5CE),
                        LiquidGlass.BackgroundBottom,
                    ),
                )
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                        ),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset(1200f, 2200f),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .size(460.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.46f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LiquidGlass.Teal.copy(alpha = 0.10f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        content()
    }
}

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = LiquidGlass.Surface,
    elevation: Dp = 12.dp,
    opacity: Float = 0.94f,
): Modifier {
    val base = tint.copy(alpha = 1f)
    val topAlpha = (opacity + 0.04f).coerceAtMost(1f)
    return this
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = topAlpha),
                    base.copy(alpha = opacity),
                ),
            )
        )
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
                size = size,
            )
        }
        .border(
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color.White.copy(alpha = 0.18f),
                    ),
                ),
            ),
            shape = shape,
        )
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = LiquidGlass.Surface,
    elevation: Dp = 12.dp,
    opacity: Float = 0.94f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .liquidGlass(shape = shape, tint = tint, elevation = elevation, opacity = opacity)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun GlassIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 30.dp,
    tint: Color = LiquidGlass.TealDeep,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(18.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(LiquidGlass.SurfaceStrong, LiquidGlass.SurfaceMuted),
                )
            )
            .border(
                BorderStroke(1.dp, LiquidGlass.Hairline),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.78f)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

enum class GlassButtonStyle { Primary, Danger, Neutral }

@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null,
) {
    val background = when (style) {
        GlassButtonStyle.Primary -> listOf(Color(0xFF19D6D8), LiquidGlass.Teal, Color(0xFF009A9E))
        GlassButtonStyle.Danger -> listOf(Color(0xFFFF4A4A), LiquidGlass.Red, LiquidGlass.RedDeep)
        GlassButtonStyle.Neutral -> listOf(
            LiquidGlass.SurfaceStrong,
            LiquidGlass.SurfaceMuted,
        )
    }
    val contentColor = when {
        !enabled -> LiquidGlass.Muted.copy(alpha = 0.55f)
        style == GlassButtonStyle.Neutral -> LiquidGlass.Ink
        else -> Color.White
    }
    val borderStroke = if (style == GlassButtonStyle.Neutral) {
        BorderStroke(1.dp, LiquidGlass.Hairline)
    } else {
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (enabled) 0.62f else 0.28f),
                    Color.White.copy(alpha = 0.14f),
                ),
            ),
        )
    }
    val clickModifier = if (enabled) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Box(
        modifier = clickModifier
            .height(48.dp)
            .shadow(if (enabled) 10.dp else 0.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = if (enabled) background else listOf(
                        LiquidGlass.SurfaceMuted,
                        LiquidGlass.SurfaceMuted,
                    ),
                )
            )
            .border(borderStroke, CircleShape)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = LiquidGlass.TealDeep,
    titleTag: String? = null,
    cardTag: String? = null,
    badgeTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LiquidGlass.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .then(if (cardTag != null) Modifier.testTag(cardTag) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(32.dp),
                            tint = LiquidGlass.DialogSurface,
                            elevation = 24.dp,
                            opacity = 0.97f,
                        ),
                ) {
                GlassIconBadge(
                    icon = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .then(if (badgeTag != null) Modifier.testTag(badgeTag) else Modifier),
                )
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = LiquidGlass.Ink,
                        modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
                    )
                    content()
                }
                }
            }
        }
    }
}

@Composable
fun GlassDialogActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LiquidGlass.SurfaceStrong,
    unfocusedContainerColor = LiquidGlass.SurfaceMuted,
    focusedTextColor = LiquidGlass.Ink,
    unfocusedTextColor = LiquidGlass.Ink,
    focusedBorderColor = LiquidGlass.Teal,
    unfocusedBorderColor = LiquidGlass.Hairline,
    cursorColor = LiquidGlass.Teal,
    focusedPlaceholderColor = LiquidGlass.Muted,
    unfocusedPlaceholderColor = LiquidGlass.Muted,
    focusedLeadingIconColor = LiquidGlass.Muted,
    unfocusedLeadingIconColor = LiquidGlass.Muted,
    focusedTrailingIconColor = LiquidGlass.Muted,
    unfocusedTrailingIconColor = LiquidGlass.Muted,
)

@Composable
fun GlassSheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .width(48.dp)
            .height(5.dp)
            .clip(CircleShape)
            .background(LiquidGlass.Ink.copy(alpha = 0.24f))
            .border(1.dp, LiquidGlass.Ink.copy(alpha = 0.10f), CircleShape),
    )
}
