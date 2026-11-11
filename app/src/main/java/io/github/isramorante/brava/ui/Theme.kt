package io.github.isramorante.brava.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF1A1917)
    val black = Color(0xFF12110F)
    val panel = Color(0xFF24221E)
    val line = Color(0xFF3A372F)
    val line2 = Color(0xFF55504A)
    val ink = Color(0xFFE8DFD2)
    val soft = Color(0xFFC2B9AB)
    val mute = Color(0xFFA39A8C)
    val rust = Color(0xFFD4582A)
    val rustText = Color(0xFFF08A5D)
    val olive = Color(0xFF7D9440)
    val oliveText = Color(0xFFC5DC8A)
    val blue = Color(0xFF8FC1E3)
    val yellow = Color(0xFFE9C46A)
    val red = Color(0xFFFF7B6B)
}

val Condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

@Composable
fun BravaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.rust,
        onPrimary = C.black,
        secondary = C.olive,
        onSecondary = C.black,
        background = C.bg,
        onBackground = C.ink,
        surface = C.panel,
        onSurface = C.ink,
        surfaceVariant = C.panel,
        onSurfaceVariant = C.soft,
        outline = C.line2,
        error = C.red,
    )
    MaterialTheme(colorScheme = scheme) {
        ProvideTextStyle(TextStyle(color = C.ink, fontFamily = Condensed, fontSize = 16.sp), content)
    }
}
