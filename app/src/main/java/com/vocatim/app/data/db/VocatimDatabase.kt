package com.vocatim.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranscriptEntity::class, SegmentEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class VocatimDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN customTitle INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN detectedLanguage TEXT"
                )
            }
        }
    }
}
