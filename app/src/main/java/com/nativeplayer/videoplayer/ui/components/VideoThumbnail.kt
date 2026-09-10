package com.nativeplayer.videoplayer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nativeplayer.videoplayer.data.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

// ── In-memory LRU cache ───────────────────────────────────────────────────────

object ThumbnailCache {
    private val cache = LruCache<String, Bitmap>(100) // up to 100 bitmaps in memory

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /**
     * Evicts all entries from the in-memory cache.
     * Call this together with [ThumbnailDiskCache.clear] so both layers stay in sync.
     */
    fun clear() {
        cache.evictAll()
    }
}

// ── In-memory LRU cache for resolutions ───────────────────────────────────────

object ResolutionCache {
    private val cache = LruCache<String, String>(200)

    fun get(key: String): String? = cache.get(key)

    fun put(key: String, resolution: String) {
        cache.put(key, resolution)
    }

    fun clear() {
        cache.evictAll()
    }
}

suspend fun getVideoResolution(context: Context, videoPath: String): String? =
    withContext(Dispatchers.IO) {
        ResolutionCache.get(videoPath)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        try {
            if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                retriever.setDataSource(videoPath, HashMap<String, String>())
            } else if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(videoPath))
            } else {
                val file = File(videoPath)
                if (!file.exists()) {
                    try {
                        retriever.setDataSource(context, android.net.Uri.parse(videoPath))
                    } catch (e: Exception) {
                        return@withContext null
                    }
                } else {
                    retriever.setDataSource(file.absolutePath)
                }
            }

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val w = widthStr?.toIntOrNull()
            val h = heightStr?.toIntOrNull()

            if (w != null && h != null) {
                val minDim = minOf(w, h)
                val label = when {
                    minDim >= 4320 || (w >= 7680 || h >= 7680) -> "8K"
                    minDim >= 2160 || (w >= 3840 || h >= 3840) -> "4K"
                    minDim >= 1440 || (w >= 2560 || h >= 2560) -> "2K"
                    minDim >= 1080 || (w >= 1920 || h >= 1920) -> "1080p"
                    minDim >= 720 || (w >= 1280 || h >= 1280) -> "720p"
                    minDim >= 480 || (w >= 854 || h >= 854) -> "480p"
                    minDim >= 360 || (w >= 640 || h >= 640) -> "360p"
                    else -> "SD"
                }
                ResolutionCache.put(videoPath, label)
                label
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

@Composable
fun rememberVideoResolution(video: VideoModel): String? {
    if (!video.resolution.isNullOrEmpty()) return video.resolution

    val context = LocalContext.current
    var resolution by remember(video.urlOrPath) {
        mutableStateOf<String?>(ResolutionCache.get(video.urlOrPath))
    }

    LaunchedEffect(video.urlOrPath) {
        if (resolution == null) {
            val res = getVideoResolution(context, video.urlOrPath)
            if (res != null) {
                resolution = res
            }
        }
    }
    return resolution
}


// ── 3-tier thumbnail loader ───────────────────────────────────────────────────

/**
 * Limits simultaneous MediaMetadataRetriever extractions to 3.
 * Memory and disk cache tiers bypass this gate since they are fast.
 * Without this cap, opening a large folder fires N concurrent video-decode
 * jobs that saturate IO threads and cause scroll jank.
 */
private val extractionSemaphore = Semaphore(3)

/**
 * Returns a [Bitmap] for [videoPath] using a three-tier cache strategy:
 *
 * 1. **Memory (LruCache)** — instantaneous, survives recomposition
 * 2. **Disk (ThumbnailDiskCache)** — persists across process restarts, promoted to memory on hit
 * 3. **MediaMetadataRetriever** — extracts frame from file/URL, then saves to disk + memory
 *
 * Must be called from a coroutine (runs on [Dispatchers.IO] internally).
 */
suspend fun getVideoThumbnail(
    context: Context,
    videoPath: String,
    durationMs: Long = 0L,
    progressMs: Long = 0L
): Bitmap? =
    withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val modeName = prefs.getString("thumbnail_mode", "PERCENTAGE")
        val percentage = prefs.getInt("thumbnail_percentage", 15).coerceIn(1, 99)

        val cacheKey = "$videoPath#m$modeName#p$percentage#d$durationMs#pr$progressMs"

        // ── Tier 1: memory (no gate needed — instantaneous) ───────────────────
        ThumbnailCache.get(cacheKey)?.let { return@withContext it }

        // ── Tier 2: disk (no gate needed — fast) ──────────────────────────────
        ThumbnailDiskCache.get(context, cacheKey)?.let { cached ->
            ThumbnailCache.put(cacheKey, cached)   // promote to memory
            return@withContext cached
        }

        // ── Tier 3: MediaMetadataRetriever extraction (rate-limited to 3) ─────
        val extracted: Bitmap? = extractionSemaphore.withPermit {
            val oldPriority = Process.getThreadPriority(Process.myTid())
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Exception) {}

            val retriever = MediaMetadataRetriever()
            try {
                if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                    retriever.setDataSource(videoPath, HashMap<String, String>())
                } else if (videoPath.startsWith("content://")) {
                    retriever.setDataSource(context, android.net.Uri.parse(videoPath))
                } else {
                    val file = File(videoPath)
                    if (!file.exists()) {
                        try {
                            retriever.setDataSource(context, android.net.Uri.parse(videoPath))
                        } catch (e: Exception) {
                            return@withPermit null
                        }
                    } else {
                        retriever.setDataSource(file.absolutePath)
                    }
                }

                val durationUs: Long = if (durationMs > 0) {
                    durationMs * 1000L
                } else {
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    (durStr?.toLongOrNull() ?: 0L) * 1000L
                }

                val timeUs: Long = when (modeName) {
                    "FIRST_FRAME" -> 0L
                    "LAST_PLAYED" -> {
                        if (progressMs > 0L) {
                            progressMs * 1000L
                        } else if (durationUs > 0L) {
                            (durationUs * percentage) / 100L
                        } else {
                            400_000L
                        }
                    }
                    else -> { // PERCENTAGE
                        if (durationUs > 0L) {
                            (durationUs * percentage) / 100L
                        } else {
                            400_000L
                        }
                    }
                }

                // Check rotation & size to determine final dimensions before extracting
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotation = rotationStr?.toIntOrNull() ?: 0
                val isRotated = rotation == 90 || rotation == 270

                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rawW = widthStr?.toIntOrNull() ?: 1280
                val rawH = heightStr?.toIntOrNull() ?: 720
                val w = if (isRotated) rawH else rawW
                val h = if (isRotated) rawW else rawH

                val maxDim = 320
                val (sw, sh) = if (w > h) {
                    val ratio = w.toFloat() / h.toFloat()
                    maxDim to (maxDim / ratio).toInt()
                } else {
                    val ratio = h.toFloat() / w.toFloat()
                    (maxDim / ratio).toInt() to maxDim
                }
                val targetW = sw.coerceAtLeast(1)
                val targetH = sh.coerceAtLeast(1)

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    try {
                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                            ?: retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                            ?: retriever.getFrameAtTime(timeUs)?.let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
                            ?: retriever.getFrameAtTime(0L)?.let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
                    } catch (e: Exception) {
                        retriever.getFrameAtTime()?.let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
                    }
                } else {
                    val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime()
                    if (raw != null) {
                        Bitmap.createScaledBitmap(raw, targetW, targetH, true).also {
                            if (it != raw) raw.recycle()
                        }
                    } else {
                        null
                    }
                }

                if (bitmap != null) {
                    ThumbnailCache.put(cacheKey, bitmap)
                    ThumbnailDiskCache.put(context, cacheKey, bitmap)
                    bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try { retriever.release() } catch (_: Exception) { }
                try { Process.setThreadPriority(oldPriority) } catch (_: Exception) {}
            }
        }
        extracted
    }

// ── Composable wrapper ────────────────────────────────────────────────────────

@Composable
fun VideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    progressMs: Long = 0L,
    placeholder: @Composable () -> Unit
) {
    val context = LocalContext.current
    val cacheKey = remember(videoPath, durationMs, progressMs) {
        "$videoPath#d$durationMs#p$progressMs"
    }

    var thumbnail by remember(cacheKey) {
        mutableStateOf<Bitmap?>(ThumbnailCache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (thumbnail == null) {
            val diskCached = withContext(Dispatchers.IO) {
                ThumbnailDiskCache.get(context, cacheKey)
            }
            if (diskCached != null) {
                thumbnail = diskCached
                ThumbnailCache.put(cacheKey, diskCached)
            } else {
                delay(150L)
                val bmp = getVideoThumbnail(context, videoPath, durationMs, progressMs)
                if (bmp != null) thumbnail = bmp
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap             = thumbnail!!.asImageBitmap(),
            contentDescription = "Video Thumbnail",
            modifier           = modifier,
            contentScale       = ContentScale.Crop
        )
    } else {
        placeholder()
    }
}
