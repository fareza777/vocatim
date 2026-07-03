package com.vocatim.app.data.model

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState {
        val progress: Float
            get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }
    data object Downloaded : ModelState
    data class Failed(val message: String) : ModelState
}
