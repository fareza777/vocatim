package com.vocatim.app.data.backup

import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val sample = BackupData(
        transcripts = listOf(
            TranscriptEntity(
                id = 7,
                title = "Rapat mingguan",
                text = "Halo semua, ini transkrip uji dengan karakter unik: é, 中文, emoji 🎙️",
                language = "id",
                modelId = "small-q5_1",
                audioDurationMs = 61_000,
                processingTimeMs = 30_000,
                audioPath = "/data/should/not/survive.wav",
                status = TranscriptStatus.DONE,
                tag = "work",
                pinned = true,
                createdAt = 1_720_000_000_000,
            )
        ),
        segments = listOf(
            SegmentEntity(transcriptId = 7, startMs = 0, endMs = 2_000, text = " Halo semua,"),
            SegmentEntity(transcriptId = 7, startMs = 2_000, endMs = 5_000, text = " ini transkrip uji."),
        ),
    )

    @Test
    fun `round-trips data with correct password`() {
        val blob = BackupCodec.encrypt(sample, "kata-sandi-kuat".toCharArray())
        val restored = BackupCodec.decrypt(blob, "kata-sandi-kuat".toCharArray())

        assertEquals(1, restored.transcripts.size)
        val t = restored.transcripts[0]
        assertEquals("Rapat mingguan", t.title)
        assertEquals(sample.transcripts[0].text, t.text)
        assertEquals("work", t.tag)
        assertEquals(true, t.pinned)
        // Audio path never survives a backup.
        assertEquals(null, t.audioPath)
        assertEquals(2, restored.segments.size)
        assertEquals(" Halo semua,", restored.segments[0].text)
    }

    @Test
    fun `wrong password fails cleanly`() {
        val blob = BackupCodec.encrypt(sample, "benar".toCharArray())
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decrypt(blob, "salah".toCharArray())
        }
    }

    @Test
    fun `garbage bytes are rejected`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decrypt(ByteArray(64) { 7 }, "apapun".toCharArray())
        }
    }

    @Test
    fun `ciphertext differs between runs (random salt and iv)`() {
        val a = BackupCodec.encrypt(sample, "pw".toCharArray())
        val b = BackupCodec.encrypt(sample, "pw".toCharArray())
        assertEquals(false, a.contentEquals(b))
    }
}
