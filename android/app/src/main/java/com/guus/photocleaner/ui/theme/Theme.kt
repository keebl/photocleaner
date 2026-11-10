package com.guus.photocleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** iOS AccentColor, sRGB (0.2, 0.5, 0.9). */
val Accent = Color(0xFF3380E6)

/** Keep / discard / restore colours, matching the iOS system green, red and blue. */
object DecisionColors {
    val keepLight = Color(0xFF34C759)
    val keepDark = Color(0xFF30D158)
    val discardLight = Color(0xFFFF3B30)
    val discardDark = Color(0xFFFF453A)
    val infoLight = Color(0xFF007AFF)
    val infoDark = Color(0xFF0A84FF)
}

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7FB),
    onPrimaryContainer = Color(0xFF0B2A55),
    secondaryContainer = Color(0xFFD9E7FB),
    onSecondaryContainer = Color(0xFF0B2A55),
    background = Color.White,
    surface = Color.White,
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE9E9EF),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E2747),
    onPrimaryContainer = Color(0xFFD9E7FB),
    secondaryContainer = Color(0xFF0E2747),
    onSecondaryContainer = Color(0xFFD9E7FB),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
)

/**
 * App theme: Material 3 with the iOS accent colour and its true-black dark mode. Dynamic
 * color is intentionally off (spec: the app keeps its own accent).
 */
@Composable
fun PhotoCleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
