package com.vocatim.app.data.backup

import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class BackupData(
    val transcripts: List<TranscriptEntity>,
    val segments: List<SegmentEntity>,
)

/**
 * Password-encrypted backup of the text data (transcripts + segments).
 * Layout: MAGIC(4) | salt(16) | iv(12) | AES-256-GCM ciphertext of JSON.
 * Audio files and models are intentionally excluded (size).
 */
object BackupCodec {
    private val MAGIC = byteArrayOf(0x56, 0x42, 0x4B, 0x31) // "VBK1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encrypt(data: BackupData, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(toJson(data).toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: CharArray): BackupData {
        if (blob.size < MAGIC.size + SALT_LEN + IV_LEN + 16 ||
            !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) {
            throw BackupFormatException("Not a Vocatim backup file")
        }
        val salt = blob.copyOfRange(4, 4 + SALT_LEN)
        val iv = blob.copyOfRange(4 + SALT_LEN, 4 + SALT_LEN + IV_LEN)
        val ciphertext = blob.copyOfRange(4 + SALT_LEN + IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val json = try {
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM auth failure == wrong password or corrupted file.
            throw BackupFormatException("Wrong password or corrupted backup", e)
        }
        return fromJson(json)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(
            PBEKeySpec(password, salt, KEY_ITERATIONS, KEY_BITS)
        )
        return SecretKeySpec(key.encoded, "AES")
    }

    private fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("transcripts", JSONArray().apply {
            data.transcripts.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("text", t.text)
                    put("language", t.language)
                    put("modelId", t.modelId)
                    put("audioDurationMs", t.audioDurationMs)
                    put("processingTimeMs", t.processingTimeMs)
                    put("status", t.status)
                    put("sourceName", t.sourceName ?: JSONObject.NULL)
                    put("translate", t.translate)
                    put("customTitle", t.customTitle)
                    put("detectedLanguage", t.detectedLanguage ?: JSONObject.NULL)
                    put("pinned", t.pinned)
                    put("tag", t.tag ?: JSONObject.NULL)
                    put("createdAt", t.createdAt)
                })
            }
        })
        root.put("segments", JSONArray().apply {
            data.segments.forEach { s ->
                put(JSONObject().apply {
                    put("transcriptId", s.transcriptId)
                    put("startMs", s.startMs)
                    put("endMs", s.endMs)
                    put("text", s.text)
                })
            }
        })
        return root.toString()
    }

    private fun fromJson(json: String): BackupData = try {
        val root = JSONObject(json)
        val transcripts = mutableListOf<TranscriptEntity>()
        val tArray = root.getJSONArray("transcripts")
        for (i in 0 until tArray.length()) {
            val o = tArray.getJSONObject(i)
            transcripts.add(
                TranscriptEntity(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    text = o.getString("text"),
                    language = o.getString("language"),
                    modelId = o.getString("modelId"),
                    audioDurationMs = o.getLong("audioDurationMs"),
                    processingTimeMs = o.getLong("processingTimeMs"),
                    audioPath = null, // audio is not part of backups
                    status = o.getString("status"),
                    sourceName = o.optString("sourceName").ifEmpty { null },
                    translate = o.optBoolean("translate"),
                    customTitle = o.optBoolean("customTitle"),
                    detectedLanguage = o.optString("detectedLanguage").ifEmpty { null },
                    pinned = o.optBoolean("pinned"),
                    tag = o.optString("tag").ifEmpty { null },
                    createdAt = o.getLong("createdAt"),
                )
            )
        }
        val segments = mutableListOf<SegmentEntity>()
        val sArray = root.getJSONArray("segments")
        for (i in 0 until sArray.length()) {
            val o = sArray.getJSONObject(i)
            segments.add(
                SegmentEntity(
                    transcriptId = o.getLong("transcriptId"),
                    startMs = o.getLong("startMs"),
                    endMs = o.getLong("endMs"),
                    text = o.getString("text"),
                )
            )
        }
        BackupData(transcripts, segments)
    } catch (e: org.json.JSONException) {
        throw BackupFormatException("Corrupted backup content", e)
    }
}
