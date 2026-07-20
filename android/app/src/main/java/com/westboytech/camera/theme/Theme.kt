package com.westboytech.camera.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

object WestboyColors {
    // #09090A — ultra-dark cinematic base
    val Background = Color(0xFF09090A)
    val NeonCyan = Color(0xFF00F2FF)
    val PanelStroke = Color.White.copy(alpha = 0.12f)
    val DangerRed = Color(0xFFFF3D3D)
}

private val DarkColors = darkColorScheme(
    background = WestboyColors.Background,
    surface = WestboyColors.Background,
    primary = WestboyColors.NeonCyan
)

@Composable
fun WestboyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

/**
 * Frosted-glass panel look. True blur-behind-content requires
 * Android 12+ (`Modifier.blur` / `RenderEffect`); this modifier applies
 * a semi-transparent scrim + border as a baseline that works on all API
 * levels, and should be paired with `Modifier.blur()` on S+ devices for
 * a true backdrop blur behind camera preview.
 */
fun Modifier.glassPanel(cornerRadius: Int = 20): Modifier = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
        )
    )
    .border(1.dp, WestboyColors.PanelStroke, RoundedCornerShape(cornerRadius.dp))
    .padding(1.dp)
