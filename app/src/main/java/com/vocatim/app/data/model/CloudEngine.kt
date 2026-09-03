package com.vocatim.app.data.model

/**
 * Marker for the cloud transcription engine.
 *
 * Stored on a transcript in the same slot as a whisper model id or
 * [ParakeetModel.ID], so a note always records which engine produced it —
 * useful when comparing a cloud transcript against a local one.
 *
 * There is no model file: the work happens on the provider's servers, using
 * the key the user configured under Cloud AI.
 */
object CloudEngine {
    const val ID = "cloud-transcribe"
}
