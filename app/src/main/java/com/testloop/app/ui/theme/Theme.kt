package com.testloop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand600 = Color(0xFF2639C6)
val Brand700 = Color(0xFF2639C6)
val Brand100 = Color(0xFFE5E8FF)
val Ink950 = Color(0xFF202322)
val Ink700 = Color(0xFF4C514D)
val Ink500 = Color(0xFF747A74)
val Canvas = Color(0xFFF7F4EE)
val Divider = Color(0xFFE1DDD4)
val Success700 = Color(0xFF2F5947)
val Success100 = Color(0xFFE1EAE3)
val Attention700 = Color(0xFF98552F)
val Attention100 = Color(0xFFF4E2D6)
val Danger700 = Color(0xFF9C3E3B)
val Danger100 = Color(0xFFF4DDDB)

private val LightScheme = lightColorScheme(
    primary = Brand600, onPrimary = Color.White, primaryContainer = Brand100, onPrimaryContainer = Ink950,
    secondary = Brand700, background = Canvas, onBackground = Ink950, surface = Color(0xFFFFFCF7),
    onSurface = Ink950, surfaceVariant = Color(0xFFEFEAE1), onSurfaceVariant = Ink700,
    outlineVariant = Divider, error = Danger700, errorContainer = Danger100
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9C1FF), onPrimary = Color(0xFF101A68), primaryContainer = Color(0xFF2639C6),
    background = Color(0xFF151916), onBackground = Color(0xFFF5F2EA), surface = Color(0xFF20251F),
    onSurface = Color(0xFFF5F2EA), surfaceVariant = Color(0xFF303830), onSurfaceVariant = Color(0xFFC5CBC2),
    outlineVariant = Color(0xFF394039), error = Color(0xFFFFB4AB)
)

@Composable
fun TestLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}
