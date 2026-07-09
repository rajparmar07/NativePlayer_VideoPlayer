package com.example.data

data class VideoModel(
    val id: String,
    val title: String,
    val urlOrPath: String,
    val duration: Long = 0,
    val size: Long = 0,
    val isLocal: Boolean = false,
    val isOffline: Boolean = false,
    val isStream: Boolean = false,
    val subtitleUrlOrPath: String? = null,
    val resolution: String? = null
) {
    companion object {
        fun parseResolutionLabel(resolutionStr: String?): String? {
            if (resolutionStr.isNullOrBlank()) return null
            val parts = resolutionStr.split('x')
            if (parts.size != 2) return null
            val w = parts[0].toIntOrNull() ?: return null
            val h = parts[1].toIntOrNull() ?: return null
            val minDim = minOf(w, h)
            return when {
                minDim >= 4320 || (w >= 7680 || h >= 7680) -> "8K"
                minDim >= 2160 || (w >= 3840 || h >= 3840) -> "4K"
                minDim >= 1440 || (w >= 2560 || h >= 2560) -> "2K"
                minDim >= 1080 || (w >= 1920 || h >= 1920) -> "1080p"
                minDim >= 720 || (w >= 1280 || h >= 1280) -> "720p"
                minDim >= 480 || (w >= 854 || h >= 854) -> "480p"
                minDim >= 360 || (w >= 640 || h >= 640) -> "360p"
                else -> "SD"
            }
        }
    }
}

