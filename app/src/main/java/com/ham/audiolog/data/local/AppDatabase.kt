package com.ham.audiolog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ham.audiolog.data.model.AudioMarkerEntity
import com.ham.audiolog.data.model.RecordingSessionEntity

@Database(
    entities = [RecordingSessionEntity::class, AudioMarkerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun markerDao(): MarkerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ham_audio_log.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
