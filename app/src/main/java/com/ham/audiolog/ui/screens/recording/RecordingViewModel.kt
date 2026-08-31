package com.ham.audiolog.ui.screens.recording

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.ham.audiolog.service.QsoAudioRecorderService
import kotlinx.coroutines.flow.StateFlow

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    val recordingState: StateFlow<com.ham.audiolog.service.RecordingServiceState> =
        QsoAudioRecorderService.recordingState

    fun startRecording(title: String = "") {
        val intent = Intent(getApplication(), QsoAudioRecorderService::class.java).apply {
            action = QsoAudioRecorderService.ACTION_START_RECORDING
            putExtra(QsoAudioRecorderService.EXTRA_SESSION_TITLE, title)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopRecording() {
        val intent = Intent(getApplication(), QsoAudioRecorderService::class.java).apply {
            action = QsoAudioRecorderService.ACTION_STOP_RECORDING
        }
        getApplication<Application>().startService(intent)
    }

    fun triggerMark() {
        val intent = Intent(getApplication(), QsoAudioRecorderService::class.java).apply {
            action = QsoAudioRecorderService.ACTION_TRIGGER_MARK
        }
        getApplication<Application>().startService(intent)
    }
}
