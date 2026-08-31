package com.ham.audiolog.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ham.audiolog.MainActivity
import com.ham.audiolog.data.local.AppDatabase
import com.ham.audiolog.data.model.RecordingSessionEntity
import com.ham.audiolog.data.repository.RecordingRepository
import com.ham.audiolog.domain.manager.AudioMarkerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class QsoAudioRecorderService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var sessionStartTimeUtc: Long = 0
    private var currentSessionId: Long = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    private lateinit var repository: RecordingRepository
    private lateinit var markerManager: AudioMarkerManager

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = RecordingRepository(db.sessionDao(), db.markerDao())
        markerManager = AudioMarkerManager(this, repository)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val title = intent.getStringExtra(EXTRA_SESSION_TITLE) ?: ""
                startRecording(title)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_TRIGGER_MARK -> {
                triggerMark()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(sessionTitle: String) {
        if (_recordingState.value.isRecording) return

        // 强互斥保护：开始录音前立即停止任何历史录音播放并清除通知
        com.ham.audiolog.domain.manager.AudioPlayerManager.stopAll()

        try {
            val recordDir = getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                ?: File(filesDir, "Recordings").apply { mkdirs() }
            if (!recordDir.exists()) recordDir.mkdirs()


            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "QSO_REC_${timestampStr}.m4a"
            recordingFile = File(recordDir, fileName)

            sessionStartTimeUtc = System.currentTimeMillis()

            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(48000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }

            serviceScope.launch(Dispatchers.IO) {
                val newSession = RecordingSessionEntity(
                    fileName = fileName,
                    filePath = recordingFile!!.absolutePath,
                    startTimeUtc = sessionStartTimeUtc,
                    sampleRate = 16000,
                    sessionTitle = sessionTitle
                )
                currentSessionId = repository.insertSession(newSession)
                val newState = _recordingState.value.copy(
                    isRecording = true,
                    sessionId = currentSessionId,
                    startTimeUtc = sessionStartTimeUtc,
                    fileName = fileName,
                    markerCount = 0
                )
                _recordingState.value = newState
                com.ham.audiolog.ui.widget.HamAudioLogWidgetProvider.updateAllWidgets(this@QsoAudioRecorderService, newState)
            }

            // Android 16 原生强类型前台麦克风服务启动
            startForeground(
                NOTIFICATION_ID,
                buildNotification(0, 0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

            startTimers()

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun startTimers() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - sessionStartTimeUtc
                val updatedState = _recordingState.value.copy(durationMs = elapsed)
                _recordingState.value = updatedState
                updateNotification(elapsed, updatedState.markerCount)
                com.ham.audiolog.ui.widget.HamAudioLogWidgetProvider.updateAllWidgets(this@QsoAudioRecorderService, updatedState)
                delay(1000)
            }
        }

        amplitudeJob?.cancel()
        amplitudeJob = serviceScope.launch {
            while (isActive) {
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                _recordingState.value = _recordingState.value.copy(amplitude = amp)
                delay(100)
            }
        }
    }

    private fun triggerMark() {
        if (!_recordingState.value.isRecording || currentSessionId == 0L) return

        serviceScope.launch {
            val offsetMs = System.currentTimeMillis() - sessionStartTimeUtc
            val marker = markerManager.addMarker(
                sessionId = currentSessionId,
                offsetMs = offsetMs,
                recordingFileName = _recordingState.value.fileName
            )
            val newCount = _recordingState.value.markerCount + 1
            val updatedState = _recordingState.value.copy(
                markerCount = newCount,
                lastMarker = marker
            )
            _recordingState.value = updatedState
            updateNotification(updatedState.durationMs, newCount)
            com.ham.audiolog.ui.widget.HamAudioLogWidgetProvider.updateAllWidgets(this@QsoAudioRecorderService, updatedState)
        }
    }

    private fun stopRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - sessionStartTimeUtc

        if (currentSessionId != 0L) {
            serviceScope.launch(Dispatchers.IO) {
                repository.getSessionById(currentSessionId)?.let { session ->
                    repository.updateSession(
                        session.copy(
                            endTimeUtc = endTime,
                            durationMs = duration
                        )
                    )
                }
            }
        }

        val standbyState = RecordingServiceState()
        _recordingState.value = standbyState
        com.ham.audiolog.ui.widget.HamAudioLogWidgetProvider.updateAllWidgets(this, standbyState)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "通联录音服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "正在录制通联音频与时间锚点"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(durationMs: Long, markerCount: Int): Notification {
        val totalSec = durationMs / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val timeStr = "%02d:%02d".format(mm, ss)

        val openAppIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val markIntent = Intent(this, QsoAudioRecorderService::class.java).apply {
            action = ACTION_TRIGGER_MARK
        }
        val markPendingIntent = PendingIntent.getService(
            this, 1, markIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, QsoAudioRecorderService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 正在录制通联语音 ($timeStr)")
            .setContentText("已标记锚点: $markerCount 次 · 点击打点即可记录")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_input_add, "＋ MARK 打点", markPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "⏹ 结束录音", stopPendingIntent)
            .build()
    }

    private fun updateNotification(durationMs: Long, markerCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(durationMs, markerCount))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaRecorder?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "qso_recorder_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.ham.audiolog.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.ham.audiolog.STOP_RECORDING"
        const val ACTION_TRIGGER_MARK = "com.ham.audiolog.TRIGGER_MARK"
        const val EXTRA_SESSION_TITLE = "extra_session_title"

        private val _recordingState = MutableStateFlow(RecordingServiceState())
        val recordingState = _recordingState.asStateFlow()
    }
}

data class RecordingServiceState(
    val isRecording: Boolean = false,
    val sessionId: Long = 0,
    val startTimeUtc: Long = 0,
    val durationMs: Long = 0,
    val amplitude: Int = 0,
    val markerCount: Int = 0,
    val fileName: String = "",
    val lastMarker: com.ham.audiolog.data.model.AudioMarkerEntity? = null
)
