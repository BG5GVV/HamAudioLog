package com.ham.audiolog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 录音会话实体
 */
@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val startTimeUtc: Long,
    val endTimeUtc: Long? = null,
    val durationMs: Long = 0,
    val sampleRate: Int = 16000,
    val markerCount: Int = 0,
    val sessionTitle: String = ""
)
