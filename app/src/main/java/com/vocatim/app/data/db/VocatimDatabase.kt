package com.vocatim.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptEntity::class, SegmentEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class VocatimDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao
}
