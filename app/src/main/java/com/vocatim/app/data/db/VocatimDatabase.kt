package com.vocatim.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranscriptEntity::class, SegmentEntity::class, AttachmentEntity::class],
    version = 9,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transcripts ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE transcripts ADD COLUMN tag TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcripts ADD COLUMN summary TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS attachments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "transcriptId INTEGER NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(transcriptId) REFERENCES transcripts(id) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_attachments_transcriptId " +
                        "ON attachments(transcriptId)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcripts ADD COLUMN summarySource TEXT")
                db.execSQL("ALTER TABLE transcripts ADD COLUMN markers TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcripts ADD COLUMN minutes TEXT")
            }
        }
    }
}
