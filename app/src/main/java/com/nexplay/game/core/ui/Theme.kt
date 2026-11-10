package com.nexplay.game.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object NexColors {
    val Bg = Color(0xFF0B0F1E)
    val Surface = Color(0xFF151B34)
    val SurfaceHi = Color(0xFF1E2646)
    val Violet = Color(0xFF7C4DFF)
    val Cyan = Color(0xFF00E5FF)
    val Pink = Color(0xFFFF4081)
    val Amber = Color(0xFFFFB300)
    val Green = Color(0xFF00E676)
    val Red = Color(0xFFFF5252)
    val Text = Color(0xFFEAEFFF)
    val TextDim = Color(0xFF9AA4C7)
    val Gold = Color(0xFFFFD54F)
    val Silver = Color(0xFFCFD8DC)
    val Bronze = Color(0xFFD7895B)

    val Brand = Brush.linearGradient(listOf(Violet, Cyan))
    val CardCheckers = listOf(Color(0xFF7C4DFF), Color(0xFF3D5AFE))
    val CardChess = listOf(Color(0xFF00BFA5), Color(0xFF00B0FF))
    val CardBackgammon = listOf(Color(0xFFFF6D00), Color(0xFFFF4081))
    val CardDomino = listOf(Color(0xFFAA00FF), Color(0xFFE040FB))
}

private val DarkScheme = darkColorScheme(
    primary = NexColors.Violet,
    onPrimary = Color.White,
    secondary = NexColors.Cyan,
    onSecondary = Color.Black,
    tertiary = NexColors.Amber,
    background = NexColors.Bg,
    onBackground = NexColors.Text,
    surface = NexColors.Surface,
    onSurface = NexColors.Text,
    surfaceVariant = NexColors.SurfaceHi,
    onSurfaceVariant = NexColors.TextDim,
    error = NexColors.Red,
    outline = Color(0xFF39426E)
)

/** The app is always dark: premium gaming look. */
@Composable
fun NexTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val unused = isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
