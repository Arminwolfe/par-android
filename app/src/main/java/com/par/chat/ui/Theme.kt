package com.par.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF5B7CFA)
val Cyan = Accent
val Violet = Color(0xFF7A5CDB)
val Navy = Color(0xFF0C0E12)
val Panel = Color(0xFF14171C)
val PanelHigh = Color(0xFF232833)
val InBubble = Color(0xFF232832)
val Muted = Color(0xFF8B93A7)
val Good = Color(0xFF3DDC97)
val Bad = Color(0xFFFF5C6C)
val OutBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Violet, Color(0xFF5B6FE0)))
val WallpaperBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0E1018), Color(0xFF1B1536), Color(0xFF0D1B2E)))

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.White,
    secondary = Violet,
    background = Navy,
    onBackground = Color(0xFFE8F0FF),
    surface = Panel,
    onSurface = Color(0xFFE8F0FF),
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    error = Bad,
)

@Composable
fun ParTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
