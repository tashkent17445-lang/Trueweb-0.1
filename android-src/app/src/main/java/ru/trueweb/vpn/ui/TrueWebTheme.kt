package ru.trueweb.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class TrueWebThemeMode { LIGHT, DARK }

val TrueWebGreen = Color(0xFF309326)

private val LightColors = lightColorScheme(
    primary = TrueWebGreen,
    onPrimary = Color.White,
    secondary = TrueWebGreen,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111315),
    onSurface = Color(0xFF111315),
    onSurfaceVariant = Color(0xFF5D6268),
    outline = Color(0xFFE1E4E8),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = TrueWebGreen,
    onPrimary = Color.White,
    secondary = TrueWebGreen,
    background = Color.Black,
    surface = Color(0xFF0C0C0C),
    surfaceVariant = Color(0xFF1B1B1B),
    onBackground = Color(0xFFF7F7F7),
    onSurface = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFFC4C4C4),
    outline = Color(0xFF2E2E2E),
    error = Color(0xFFFF6B6B)
)

fun trueWebBackground(mode: TrueWebThemeMode): Brush = when (mode) {
    TrueWebThemeMode.LIGHT -> Brush.verticalGradient(listOf(Color.White, Color.White))
    TrueWebThemeMode.DARK -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
}

@Composable
fun TrueWebTheme(
    mode: TrueWebThemeMode = TrueWebThemeMode.DARK,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (mode == TrueWebThemeMode.LIGHT) LightColors else DarkColors,
        content = content
    )
}
