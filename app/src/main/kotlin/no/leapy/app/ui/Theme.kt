package no.leapy.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B0D11)
    val Card = Color(0xFF15181E)
    val CardHigh = Color(0xFF1E222A)
    val Line = Color(0xFF22262E)
    val On = Color(0xFFF2F4F7)
    val Sub = Color(0xFF8B93A1)
    val Faint = Color(0xFF555C68)
    val Accent = Color(0xFF3DDC97)
    val Heat = Color(0xFFFF8A4C)
    val Warn = Color(0xFFFFB547)
    val Danger = Color(0xFFFF6B6B)
}

private val Scheme = darkColorScheme(
    primary = C.Accent, onPrimary = Color(0xFF04170E),
    secondary = C.On, onSecondary = C.Bg,
    background = C.Bg, onBackground = C.On,
    surface = C.Bg, onSurface = C.On,
    surfaceVariant = C.Card, onSurfaceVariant = C.Sub,
    surfaceContainer = C.Card, surfaceContainerHigh = C.CardHigh, surfaceContainerHighest = C.CardHigh,
    outline = C.Line, outlineVariant = C.Line,
    error = C.Danger,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LeapyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
