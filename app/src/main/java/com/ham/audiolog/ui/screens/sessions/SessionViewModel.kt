package com.ham.audiolog.ui.screens.sessions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ham.audiolog.data.local.AppDatabase
import com.ham.audiolog.data.model.AudioMarkerEntity
import com.ham.audiolog.data.model.RecordingSessionEntity
import com.ham.audiolog.data.repository.RecordingRepository
import com.ham.audiolog.domain.manager.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SessionUiState(
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val selectedSession: RecordingSessionEntity? = null,
    val markers: List<AudioMarkerEntity> = emptyList(),
    val editingMarker: AudioMarkerEntity? = null,
    val exportShareContent: Pair<String, String>? = null,
    val infoMessage: String? = null
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository
    val playerManager: AudioPlayerManager = AudioPlayerManager(application)

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = RecordingRepository(db.sessionDao(), db.markerDao())

        viewModelScope.launch {
            repository.getAllSessions().collect { list ->
                _uiState.update { it.copy(sessions = list) }
            }
        }

        // 启动时自动同步扫描物理录音文件，确保历史已存录音 100% 不丢失
        syncPhysicalRecordingFiles(application)
    }

    /**
     * 自动扫描物理磁盘中的录音文件，若有未入库的录音则自动补全索引
     */
    private fun syncPhysicalRecordingFiles(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val candidateDirs = listOfNotNull(
                    application.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS),
                    File(application.filesDir, "Recordings")
                )
                val audioFiles = candidateDirs
                    .filter { it.exists() && it.isDirectory }
                    .flatMap { it.listFiles()?.toList() ?: emptyList() }
                    .filter { it.isFile && (it.name.endsWith(".m4a", ignoreCase = true) || it.name.endsWith(".aac", ignoreCase = true) || it.name.endsWith(".wav", ignoreCase = true)) }

                val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

                for (file in audioFiles) {
                    val existing = repository.getSessionByPath(file.absolutePath)
                    if (existing == null) {
                        // 解析时长与时间戳
                        var durationMs = 0L
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(file.absolutePath)
                            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            durationMs = durationStr?.toLongOrNull() ?: 0L
                        } catch (_: Exception) {
                        } finally {
                            try { mmr.release() } catch (_: Exception) {}
                        }

                        var startTime = file.lastModified()
                        // 尝试从文件名解析时间：QSO_REC_yyyyMMdd_HHmmss.m4a
                        val regex = Regex("QSO_REC_(\\d{8}_\\d{6})")
                        regex.find(file.name)?.let { match ->
                            val timePart = match.groupValues[1]
                            try {
                                startTime = nameFormat.parse(timePart)?.time ?: startTime
                            } catch (_: Exception) {}
                        }

                        val newSession = RecordingSessionEntity(
                            fileName = file.name,
                            filePath = file.absolutePath,
                            startTimeUtc = startTime,
                            endTimeUtc = startTime + durationMs,
                            durationMs = durationMs,
                            sampleRate = 16000,
                            markerCount = 0,
                            sessionTitle = ""
                        )
                        repository.insertSession(newSession)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectSession(session: RecordingSessionEntity?) {
        _uiState.update { it.copy(selectedSession = session) }
        if (session != null) {
            // 预先准备该录音文件的播放器状态（不自动播放），以便界面立即获取总时长和就绪状态
            playerManager.loadFile(session.filePath, autoPlay = false, startPositionMs = 0L)
            viewModelScope.launch {
                repository.getMarkersForSession(session.id).collect { markerList ->
                    _uiState.update { it.copy(markers = markerList) }
                }
            }
        } else {
            playerManager.stop()
        }
    }

    /**
     * 播放或暂停当前会话完整录音
     */
    fun togglePlayPause(session: RecordingSessionEntity? = null) {
        val targetSession = session ?: _uiState.value.selectedSession ?: return
        playerManager.togglePlayPause(targetSession.filePath)
    }

    /**
     * 从头（或指定位置）开始播放完整录音
     */
    fun playFullRecording(session: RecordingSessionEntity? = null, startPositionMs: Long = 0L) {
        val targetSession = session ?: _uiState.value.selectedSession ?: return
        playerManager.playFile(targetSession.filePath, startPositionMs)
    }

    fun playMarker(marker: AudioMarkerEntity) {
        val session = _uiState.value.selectedSession ?: return
        playerManager.playMarker(session.filePath, marker)
    }

    fun onEditMarker(marker: AudioMarkerEntity?) {
        _uiState.update { it.copy(editingMarker = marker) }
    }

    fun updateMarker(marker: AudioMarkerEntity) {
        viewModelScope.launch {
            repository.updateMarker(marker)
            _uiState.update {
                it.copy(
                    editingMarker = null,
                    infoMessage = "已保存标记 #${marker.markerIndex} 信息"
                )
            }
        }
    }

    fun deleteMarker(marker: AudioMarkerEntity) {
        viewModelScope.launch {
            repository.deleteMarker(marker)
        }
    }

    fun deleteSession(session: RecordingSessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_uiState.value.selectedSession?.id == session.id) {
                playerManager.stop()
                _uiState.update { it.copy(selectedSession = null) }
            }
        }
    }

    fun exportAdif() {
        val session = _uiState.value.selectedSession ?: return
        val markers = _uiState.value.markers

        val sb = StringBuilder()
        sb.append("HamAudioLog QSO Export\n")
        sb.append("<ADIF_VER:5>3.1.7\n")
        sb.append("<PROGRAMID:11>HamAudioLog\n")
        sb.append("<EOH>\n\n")

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val timeFormat = SimpleDateFormat("HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        markers.forEach { m ->
            val dateStr = dateFormat.format(Date(m.utcTimestamp))
            val timeStr = timeFormat.format(Date(m.utcTimestamp))
            val call = if (m.callsign.isNotBlank()) m.callsign else "UNKNOWN_${m.markerIndex}"
            val rstS = m.rstSent.ifBlank { "59" }
            val rstR = m.rstRcvd.ifBlank { "59" }
            val band = if (m.band.isNotBlank()) m.band else "40m"
            val mode = if (m.mode.isNotBlank()) m.mode else "SSB"

            sb.append("<QSO_DATE:${dateStr.length}>$dateStr")
            sb.append("<TIME_ON:${timeStr.length}>$timeStr")
            sb.append("<CALL:${call.length}>$call")
            sb.append("<RST_SENT:${rstS.length}>$rstS")
            sb.append("<RST_RCVD:${rstR.length}>$rstR")
            sb.append("<BAND:${band.length}>$band")
            sb.append("<MODE:${mode.length}>$mode")
            if (m.remark.isNotBlank()) {
                sb.append("<COMMENT:${m.remark.length}>${m.remark}")
            }
            sb.append("<EOR>\n")
        }

        val filename = "HamAudioLog_${session.fileName.removeSuffix(".m4a")}.adi"
        _uiState.update { it.copy(exportShareContent = Pair(filename, sb.toString())) }
    }

    fun clearExportShareContent() = _uiState.update { it.copy(exportShareContent = null) }
    fun dismissInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

