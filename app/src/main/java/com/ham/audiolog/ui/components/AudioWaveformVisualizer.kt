package com.ham.audiolog.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun AudioWaveformVisualizer(
    isRecording: Boolean,
    amplitude: Int,
    modifier: Modifier = Modifier
) {
    val barCount = 32
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "waveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // 归一化振幅 (0.0 ~ 1.0)
    val normAmp = (amplitude.toFloat() / 32767f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidth = totalWidth / (barCount * 1.5f)
        val spacing = barWidth * 0.5f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing) + spacing / 2f
            val baseHeightFactor = if (isRecording) {
                val wave = kotlin.math.sin((i.toFloat() / barCount * 3.14f * 2f) + phase * 6.28f)
                val dynamicHeight = (normAmp * 0.7f + 0.15f) + (wave.toFloat() * 0.15f)
                dynamicHeight.coerceIn(0.08f, 1.0f)
            } else {
                0.06f
            }

            val barHeight = totalHeight * baseHeightFactor
            val topY = (totalHeight - barHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isRecording) {
                        listOf(primaryColor, secondaryColor)
                    } else {
                        listOf(Color(0xFF4A5568), Color(0xFF2D3748))
                    }
                ),
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
