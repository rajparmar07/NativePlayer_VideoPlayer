package com.nativeplayer.ui.components

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AudioTrackInfo(
    val index: Int,
    val language: String,
    val title: String,
    val mimeType: String,
    val channelCount: Int,
    val sampleRate: Int,
    val bitrate: Int
) {
    fun getFormattedCodec(): String {
        return when {
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
            mimeType.contains("ac3", ignoreCase = true) -> "AC3 (Dolby Digital)"
            mimeType.contains("eac3", ignoreCase = true) -> "E-AC3 (Dolby Digital Plus)"
            mimeType.contains("dts", ignoreCase = true) -> "DTS"
            mimeType.contains("opus", ignoreCase = true) -> "Opus"
            mimeType.contains("vorbis", ignoreCase = true) -> "Vorbis"
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
            else -> mimeType.substringAfter("/")
        }
    }

    fun getFormattedChannels(): String {
        return when (channelCount) {
            1 -> "Mono (1 ch)"
            2 -> "Stereo (2 ch)"
            6 -> "5.1 Surround (6 ch)"
            8 -> "7.1 Surround (8 ch)"
            else -> if (channelCount > 0) "$channelCount Channels" else "Unknown"
        }
    }
}

data class SubtitleTrackInfo(
    val index: Int,
    val language: String,
    val title: String,
    val mimeType: String
) {
    fun getFormattedType(): String {
        return when {
            mimeType.contains("x-subrip", ignoreCase = true) || mimeType.contains("subrip", ignoreCase = true) -> "SubRip (SRT)"
            mimeType.contains("vtt", ignoreCase = true) -> "WebVTT"
            mimeType.contains("ssa", ignoreCase = true) || mimeType.contains("ass", ignoreCase = true) -> "ASS / SSA"
            mimeType.contains("pgs", ignoreCase = true) -> "PGS (HD MV)"
            mimeType.contains("cea-608", ignoreCase = true) || mimeType.contains("608", ignoreCase = true) -> "CEA-608"
            mimeType.contains("cea-708", ignoreCase = true) || mimeType.contains("708", ignoreCase = true) -> "CEA-708"
            mimeType.contains("ttml", ignoreCase = true) -> "TTML"
            else -> mimeType.substringAfter("/")
        }
    }
}

data class VideoMetadata(
    val title: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val durationFormatted: String,
    val durationMs: Long,
    val containerFormat: String,
    val resolution: String,
    val videoCodec: String,
    val frameRate: Float,
    val bitrateKbps: Long,
    val lastModifiedFormatted: String,
    val audioTracks: List<AudioTrackInfo>,
    val subtitleTracks: List<SubtitleTrackInfo>
)

object VideoMetadataExtractor {

    suspend fun extractMetadataAsync(context: Context, filePath: String): VideoMetadata {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            extractMetadata(context, filePath)
        }
    }

    fun extractMetadata(context: Context, filePath: String): VideoMetadata {
        val file = File(filePath)
        val title = file.name
        val sizeBytes = if (file.exists()) file.length() else 0L
        val lastModified = if (file.exists()) file.lastModified() else 0L
        val lastModifiedFormatted = if (lastModified > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastModified))
        } else "Unknown"

        val containerExtension = file.extension.uppercase().ifEmpty { "UNKNOWN" }
        
        var durationMs = 0L
        var width = 0
        var height = 0
        var videoCodec = "Unknown"
        var frameRate = 0f
        var bitrate = 0L
        var mimeType = ""

        val audioTracks = mutableListOf<AudioTrackInfo>()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            width = wStr?.toIntOrNull() ?: 0
            height = hStr?.toIntOrNull() ?: 0

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            bitrate = (bitrateStr?.toLongOrNull() ?: 0L) / 1000 // in kbps

            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mimeStr.isNullOrEmpty()) {
                mimeType = mimeStr
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                frameRate = fpsStr?.toFloatOrNull() ?: 0f
            }
        } catch (e: Exception) {
            Log.e("VideoMetadataExtractor", "Error retriever: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }

        // Use MediaExtractor to accurately enumerate video, audio and subtitle tracks
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            val trackCount = extractor.trackCount
            var audioIndex = 1
            var subtitleIndex = 1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (trackMime.startsWith("video/")) {
                    videoCodec = formatMimeToCodec(trackMime)
                    if (width == 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (height == 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (frameRate == 0f && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = try {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE)
                        } catch (e: Exception) {
                            format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    }
                } else if (trackMime.startsWith("audio/")) {
                    val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) ?: "und" else "und"
                    val trackTitle = if (format.containsKey("title")) format.getString("title") ?: "Track $audioIndex" else "Track $audioIndex"
                    val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                    val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                    val trackBitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE) else 0

                    audioTracks.add(
                        AudioTrackInfo(
                            index = audioIndex++,
                            language = lang,
                            title = trackTitle,
                            mimeType = trackMime,
                            channelCount = channels,
                            sampleRate = sampleRate,
                            bitrate = trackBitrate
                        )
                    )
                } else if (trackMime.startsWith("text/") || trackMime.startsWith("application/") || trackMime.contains("subtitle") || trackMime.contains("subrip") || trackMime.contains("vtt")) {
                    val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) ?: "und" else "und"
                    val trackTitle = if (format.containsKey("title")) format.getString("title") ?: "Subtitle $subtitleIndex" else "Subtitle $subtitleIndex"

                    subtitleTracks.add(
                        SubtitleTrackInfo(
                            index = subtitleIndex++,
                            language = lang,
                            title = trackTitle,
                            mimeType = trackMime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VideoMetadataExtractor", "Error extractor: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {}
        }

        val resolution = if (width > 0 && height > 0) "${width}x${height}" else "Unknown"
        val formattedDuration = formatDurationMs(durationMs)
        val formattedSize = formatSize(sizeBytes)

        return VideoMetadata(
            title = title,
            filePath = filePath,
            fileSizeFormatted = formattedSize,
            fileSizeBytes = sizeBytes,
            durationFormatted = formattedDuration,
            durationMs = durationMs,
            containerFormat = if (mimeType.isNotEmpty()) "$containerExtension (${mimeType.substringAfter('/')})" else containerExtension,
            resolution = resolution,
            videoCodec = videoCodec,
            frameRate = frameRate,
            bitrateKbps = bitrate,
            lastModifiedFormatted = lastModifiedFormatted,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    private fun formatMimeToCodec(mime: String): String {
        return when {
            mime.contains("avc", ignoreCase = true) -> "H.264 / AVC"
            mime.contains("hevc", ignoreCase = true) -> "H.265 / HEVC"
            mime.contains("vp9", ignoreCase = true) -> "VP9"
            mime.contains("vp8", ignoreCase = true) -> "VP8"
            mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1"
            mime.contains("mp4v", ignoreCase = true) || mime.contains("mpeg4", ignoreCase = true) -> "MPEG-4"
            mime.contains("mpeg2", ignoreCase = true) -> "MPEG-2"
            else -> mime.substringAfter("/")
        }
    }

    private fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return "00:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
