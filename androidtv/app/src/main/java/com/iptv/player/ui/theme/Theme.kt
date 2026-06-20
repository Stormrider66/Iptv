package com.iptv.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Accent = Color(0xFF6C5CE7)
val Accent2 = Color(0xFFA29BFE)
val Surface1 = Color(0xFF14141F)
val Surface2 = Color(0xFF1E1E2E)
val Bg = Color(0xFF0A0A0F)
val TextPrimary = Color(0xFFE8E8F0)
val TextSecondary = Color(0xFF8888A0)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun IptvTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
