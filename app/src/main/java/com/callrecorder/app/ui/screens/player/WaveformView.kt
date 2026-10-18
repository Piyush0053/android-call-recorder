package com.callrecorder.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.callrecorder.app.ui.theme.WaveformActive
import com.callrecorder.app.ui.theme.WaveformInactive

/**
 * Interactive canvas waveform rendering normalized amplitudes
 * and tracking playback progression.
 */
@Composable
fun WaveformView(
    amplitudes: List<Float>,
    progressFraction: Float, // 0.0f to 1.0f
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(amplitudes) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0.0f, 1.0f)
                    onSeek(fraction)
                }
            }
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val barCount = amplitudes.size
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(3.dp.toPx())

        amplitudes.forEachIndexed { index, amp ->
            val left = index * (barWidth + barSpacing)
            val barFraction = (index.toFloat() / barCount.toFloat())
            val isActive = barFraction <= progressFraction

            val barHeight = (canvasHeight * amp.coerceIn(0.1f, 1.0f)).coerceAtLeast(6.dp.toPx())
            val top = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                color = if (isActive) WaveformActive else WaveformInactive,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
