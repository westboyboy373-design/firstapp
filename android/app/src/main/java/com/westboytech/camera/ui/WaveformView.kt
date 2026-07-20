package com.westboytech.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.westboytech.camera.theme.WestboyColors
import com.westboytech.camera.theme.glassPanel

/**
 * Draws [amplitudes] (0..1 each) as animated bars, sourced live from
 * AudioManager's Visualizer tap on the background track's own output —
 * real audio-reactivity, not a canned loop.
 */
@Composable
fun WaveformView(amplitudes: FloatArray, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .height(44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glassPanel(cornerRadius = 16)
    ) {
        val barWidth = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val maxBarHeight = size.height
        amplitudes.forEachIndexed { index, amp ->
            val barHeight = (amp * maxBarHeight).coerceAtLeast(4f)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = WestboyColors.NeonCyan,
                topLeft = Offset(x, (maxBarHeight - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
            )
        }
    }
}
