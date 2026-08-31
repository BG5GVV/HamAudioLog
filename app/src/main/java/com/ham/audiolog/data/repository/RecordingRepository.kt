package com.ham.audiolog.data.repository

import com.ham.audiolog.data.local.MarkerDao
import com.ham.audiolog.data.local.SessionDao
import com.ham.audiolog.data.model.AudioMarkerEntity
import com.ham.audiolog.data.model.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class RecordingRepository(
    private val sessionDao: SessionDao,
    private val markerDao: MarkerDao
) {
    fun getAllSessions(): Flow<List<RecordingSessionEntity>> = sessionDao.getAllSessionsFlow()

    suspend fun getAllSessionsDirect(): List<RecordingSessionEntity> = sessionDao.getAllSessionsDirect()

    fun getSessionFlow(id: Long): Flow<RecordingSessionEntity?> = sessionDao.getSessionFlow(id)

    suspend fun getSessionById(id: Long): RecordingSessionEntity? = sessionDao.getSessionById(id)

    suspend fun getSessionByPath(filePath: String): RecordingSessionEntity? = sessionDao.getSessionByPath(filePath)

    suspend fun insertSession(session: RecordingSessionEntity): Long = sessionDao.insertSession(session)

    suspend fun updateSession(session: RecordingSessionEntity) = sessionDao.updateSession(session)

    suspend fun deleteSession(session: RecordingSessionEntity) {
        // 删除录音实体及其物理音频文件
        try {
            val file = File(session.filePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        sessionDao.deleteSession(session)
    }

    fun getMarkersForSession(sessionId: Long): Flow<List<AudioMarkerEntity>> =
        markerDao.getMarkersForSessionFlow(sessionId)

    suspend fun getMarkersDirect(sessionId: Long): List<AudioMarkerEntity> =
        markerDao.getMarkersForSession(sessionId)

    suspend fun insertMarker(marker: AudioMarkerEntity): Long {
        val id = markerDao.insertMarker(marker)
        val count = markerDao.getMarkerCount(marker.sessionId)
        sessionDao.getSessionById(marker.sessionId)?.let { session ->
            sessionDao.updateSession(session.copy(markerCount = count))
        }
        return id
    }

    suspend fun updateMarker(marker: AudioMarkerEntity) = markerDao.updateMarker(marker)

    suspend fun deleteMarker(marker: AudioMarkerEntity) {
        markerDao.deleteMarker(marker)
        val count = markerDao.getMarkerCount(marker.sessionId)
        sessionDao.getSessionById(marker.sessionId)?.let { session ->
            sessionDao.updateSession(session.copy(markerCount = count))
        }
    }
}
