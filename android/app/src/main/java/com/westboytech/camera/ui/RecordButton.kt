package com.westboytech.camera.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.westboytech.camera.theme.WestboyColors

@Composable
fun RecordButton(
    isRecording: Boolean,
    progress: Float, // 0..1, wraps for long recordings
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val diameter = 84.dp
    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 14.dp else diameter / 2,
        animationSpec = spring(dampingRatio = 0.65f), label = "corner"
    )
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) diameter * 0.42f else diameter * 0.82f,
        animationSpec = spring(dampingRatio = 0.65f), label = "size"
    )

    Box(
        modifier = Modifier.size(diameter + 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter + 14.dp)) {
            if (isRecording) {
                val sweep = 360f * progress
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Cyan, Color.Magenta, Color(0xFFFF3D9A), Color.Cyan)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    color = if (isRecording) WestboyColors.DangerRed else Color.White,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable {
                    hapticPulse(context)
                    onClick()
                }
        )
    }
}

private fun hapticPulse(context: Context) {
    val effect = VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(effect)
    }
}
