package com.ham.audiolog.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通联时间打点锚点实体
 */
@Entity(
    tableName = "audio_markers",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("utcTimestamp")]
)
data class AudioMarkerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val markerIndex: Int,
    val audioOffsetMs: Long,
    val utcTimestamp: Long,
    val localFormattedTime: String,

    // 事后核对/极简补充字段
    val callsign: String = "",
    val rstSent: String = "59",
    val rstRcvd: String = "59",
    val band: String = "",
    val mode: String = "",
    val remark: String = "",
    val isTranscribed: Boolean = false
)
