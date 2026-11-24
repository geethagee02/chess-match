package dev.geetha.chessmatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF12100E)
val Panel = Color(0xFF1C1916)
val Gold = Color(0xFFE0B15A)
val Ivory = Color(0xFFF4EDE2)
val Muted = Color(0xFFA3988A)
val Frame = Color(0xFF3B2A1E)
val LightSquare = Color(0xFFF0D9B5)
val DarkSquare = Color(0xFFB58863)
val CheckRed = Color(0xFFD4574A)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1C1408),
    background = Ink,
    surface = Panel,
    onBackground = Ivory,
    onSurface = Ivory,
    secondary = Color(0xFF8FBF9F),
    error = Color(0xFFE07A6A),
)

@Composable
fun ChessMatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
