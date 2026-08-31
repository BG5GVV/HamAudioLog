package com.ham.audiolog.domain.manager

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import com.ham.audiolog.data.model.AudioMarkerEntity
import com.ham.audiolog.data.repository.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AudioMarkerManager(
    private val context: Context,
    private val repository: RecordingRepository
) {
    private val vibratorManager = context.getSystemService(VibratorManager::class.java)
    private val localTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    suspend fun addMarker(
        sessionId: Long,
        offsetMs: Long,
        recordingFileName: String
    ): AudioMarkerEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val localTimeStr = localTimeFormat.format(Date(now))

        // 触发强劲明确的盲操打点触感震动（双脉冲节奏，清晰可感知）
        triggerHapticFeedback()

        val existingMarkers = repository.getMarkersDirect(sessionId)
        val nextIndex = existingMarkers.size + 1

        val marker = AudioMarkerEntity(
            sessionId = sessionId,
            markerIndex = nextIndex,
            audioOffsetMs = offsetMs,
            utcTimestamp = now,
            localFormattedTime = localTimeStr
        )

        val id = repository.insertMarker(marker)
        marker.copy(id = id)
    }

    /**
     * 触发针对户外强光/盲操环境优化的强劲触感震动
     * 采用双脉冲确认节奏 (80ms 中强 + 50ms 间隔 + 140ms 强劲)，大幅提升感知度
     */
    private fun triggerHapticFeedback() {
        try {
            val vibrator = vibratorManager?.defaultVibrator
                ?: context.getSystemService(android.os.Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (vibrator.hasAmplitudeControl()) {
                    val timings = longArrayOf(0, 80, 50, 140)
                    val amplitudes = intArrayOf(0, 200, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
