package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Persistent disk cache for video thumbnails.
 *
 * Storage: [Context.cacheDir]/thumbnails/
 * Key mapping: videoPath.hashCode() → "<hashcode>.jpg"
 * Format: JPEG at 80% quality (thumbnails are already ≤320px so loss is imperceptible)
 * Eviction: oldest-first (by lastModified) when total exceeds [MAX_CACHE_BYTES]
 */
object ThumbnailDiskCache {

    private const val DIR_NAME    = "thumbnails"
    private const val MAX_CACHE_BYTES = 100L * 1024 * 1024   // 100 MB cap
    private const val JPEG_QUALITY    = 80

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns a decoded [Bitmap] from disk, or null on cache miss. */
    fun get(context: Context, key: String): Bitmap? {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        return try {
            // RGB_565 = 2 bytes/pixel vs 4 bytes/pixel for ARGB_8888.
            // At 320px thumbnail size this is imperceptible but halves memory
            // pressure and speeds up decoding — helps smooth out scroll performance.
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            file.delete() // corrupt entry — remove it
            null
        }
    }

    /**
     * Writes [bitmap] to disk under [key].
     * Updates the file's last-modified time so LRU eviction stays accurate.
     */
    fun put(context: Context, key: String, bitmap: Bitmap) {
        val file = cacheFile(context, key)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            file.setLastModified(System.currentTimeMillis())
            evictIfNeeded(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Deletes every cached thumbnail file. */
    fun clear(context: Context) {
        cacheDir(context).listFiles()?.forEach { it.delete() }
    }

    /** Total bytes occupied by all cached thumbnails. */
    fun getCacheSizeBytes(context: Context): Long =
        cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** Number of thumbnail files currently on disk. */
    fun getThumbnailCount(context: Context): Int =
        cacheDir(context).listFiles()?.size ?: 0

    /** Exposed so [MemoryScreen] can show the absolute directory path if needed. */
    fun cacheDir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    // ── Internals ──────────────────────────────────────────────────────────────

    private fun cacheFile(context: Context, key: String): File =
        File(cacheDir(context), "${key.hashCode()}.jpg")

    /**
     * Removes the oldest files (by last-modified) until total size is under [MAX_CACHE_BYTES].
     * Called automatically after every [put].
     */
    private fun evictIfNeeded(context: Context) {
        val files = cacheDir(context).listFiles()
            ?.sortedBy { it.lastModified() }
            ?: return
        var totalSize = files.sumOf { it.length() }
        var index = 0
        while (totalSize > MAX_CACHE_BYTES && index < files.size) {
            totalSize -= files[index].length()
            files[index].delete()
            index++
        }
    }
}
