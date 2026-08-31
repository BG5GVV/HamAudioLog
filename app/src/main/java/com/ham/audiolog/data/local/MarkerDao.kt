package com.ham.audiolog.data.local

import androidx.room.*
import com.ham.audiolog.data.model.AudioMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: AudioMarkerEntity): Long

    @Update
    suspend fun updateMarker(marker: AudioMarkerEntity)

    @Delete
    suspend fun deleteMarker(marker: AudioMarkerEntity)

    @Query("SELECT * FROM audio_markers WHERE sessionId = :sessionId ORDER BY markerIndex ASC")
    fun getMarkersForSessionFlow(sessionId: Long): Flow<List<AudioMarkerEntity>>

    @Query("SELECT * FROM audio_markers WHERE sessionId = :sessionId ORDER BY markerIndex ASC")
    suspend fun getMarkersForSession(sessionId: Long): List<AudioMarkerEntity>

    @Query("SELECT COUNT(*) FROM audio_markers WHERE sessionId = :sessionId")
    suspend fun getMarkerCount(sessionId: Long): Int
}
