package ca.kieve.yomiyou.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White

private val DarkColorPalette = darkColors(
    primary = Black,
    primaryVariant = Black,
    onPrimary = White,

    secondary = Blue500,
    onSecondary = White,

    background = Black,
    onBackground = White,

    surface = DarkGray,
    onSurface = White
)

@Composable
fun YomiyouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
