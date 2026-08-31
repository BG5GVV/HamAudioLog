package com.ham.audiolog.data.local

import androidx.room.*
import com.ham.audiolog.data.model.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity): Long

    @Update
    suspend fun updateSession(session: RecordingSessionEntity)

    @Delete
    suspend fun deleteSession(session: RecordingSessionEntity)

    @Query("SELECT * FROM recording_sessions ORDER BY startTimeUtc DESC")
    fun getAllSessionsFlow(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions ORDER BY startTimeUtc DESC")
    suspend fun getAllSessionsDirect(): List<RecordingSessionEntity>

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions WHERE filePath = :filePath LIMIT 1")
    suspend fun getSessionByPath(filePath: String): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    fun getSessionFlow(id: Long): Flow<RecordingSessionEntity?>
}

