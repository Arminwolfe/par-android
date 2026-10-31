package com.par.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.par.chat.R

val Accent = Color(0xFF5B7CFA)
val Cyan = Accent
val Violet = Color(0xFF7A5CDB)
val Navy = Color(0xFF0C0E12)
val Panel = Color(0xFF14171C)
val PanelHigh = Color(0xFF232833)
val InBubble = Color(0xFF2B3040)
val Muted = Color(0xFF9AA3B8)
val Good = Color(0xFF2FBF86)
val Bad = Color(0xFFFF5C6C)
val OutBrush = Brush.linearGradient(listOf(Violet, Color(0xFF5B6FE0)))

/** Vazirmatn when it is bundled (the build downloads it), otherwise a Persian-capable fallback. */
val VazirFamily = FontFamily(Font(R.font.vazirmatn_regular, FontWeight.Normal), Font(R.font.vazirmatn_bold, FontWeight.Bold))

private val dark = darkColorScheme(
    primary = Accent, onPrimary = Color.White, secondary = Violet,
    background = Navy, onBackground = Color(0xFFF4F6FB),
    surface = Panel, onSurface = Color(0xFFF4F6FB),
    surfaceVariant = PanelHigh, onSurfaceVariant = Muted, error = Bad,
)
private val light = lightColorScheme(
    primary = Accent, onPrimary = Color.White, secondary = Violet,
    background = Color(0xFFEEF1F6), onBackground = Color(0xFF0F172A),
    surface = Color.White, onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6EAF2), onSurfaceVariant = Color(0xFF5B6478), error = Bad,
)

private fun TextStyle.v() = copy(fontFamily = VazirFamily)
private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
    headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
    titleLarge = base.titleLarge.v(), titleMedium = base.titleMedium.v(), titleSmall = base.titleSmall.v(),
    bodyLarge = base.bodyLarge.v().copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
    bodyMedium = base.bodyMedium.v().copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
    bodySmall = base.bodySmall.v().copy(fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)),
    labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v(),
)

/** mode: "auto" (follow the phone), "light" or "dark". */
@Composable
fun ParTheme(mode: String = "auto", content: @Composable () -> Unit) {
    val useDark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (useDark) dark else light, typography = typography, content = content)
}

@Composable
fun wallpaperBrush(): Brush =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Brush.linearGradient(listOf(Color(0xFFE4EAF4), Color(0xFFE9E4F6), Color(0xFFDDEBF7)))
    else Brush.linearGradient(listOf(Color(0xFF0E1018), Color(0xFF1B1536), Color(0xFF0D1B2E)))
