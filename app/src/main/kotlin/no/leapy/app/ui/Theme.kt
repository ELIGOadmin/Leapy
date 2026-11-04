package no.leapy.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF3DDC97)
val Warn = Color(0xFFFFB547)
val Danger = Color(0xFFFF6B6B)

private val Dark = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF00210F),
    secondary = Color(0xFF7FB7FF), onSecondary = Color(0xFF00213F),
    background = Color(0xFF0E1420), onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0E1420), onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1A2232), onSurfaceVariant = Color(0xFFA7B0C2),
    surfaceContainer = Color(0xFF161D2B), surfaceContainerHigh = Color(0xFF1C2433),
    error = Danger,
)

private val Light = lightColorScheme(
    primary = Color(0xFF00895A), onPrimary = Color.White,
    secondary = Color(0xFF2F6DB5),
    background = Color(0xFFF5F7FA), surface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFFE6EAF0), surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
)

@Composable
fun LeapyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
