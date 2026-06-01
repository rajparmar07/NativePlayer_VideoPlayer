package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailCache {
    private val cache = LruCache<String, Bitmap>(100) // Cache up to 100 thumbnails in memory

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

suspend fun getVideoThumbnail(videoPath: String): Bitmap? = withContext(Dispatchers.IO) {
    ThumbnailCache.get(videoPath)?.let { return@withContext it }

    val retriever = MediaMetadataRetriever()
    try {
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            retriever.setDataSource(videoPath, HashMap<String, String>())
        } else {
            val file = File(videoPath)
            if (file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                return@withContext null
            }
        }
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

        if (bitmap != null) {
            val maxDimension = 320
            val width = bitmap.width
            val height = bitmap.height
            val (scaledWidth, scaledHeight) = if (width > height) {
                val ratio = width.toFloat() / height.toFloat()
                maxDimension to (maxDimension / ratio).toInt()
            } else {
                val ratio = height.toFloat() / width.toFloat()
                (maxDimension / ratio).toInt() to maxDimension
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth.coerceAtLeast(1), scaledHeight.coerceAtLeast(1), true)
            ThumbnailCache.put(videoPath, scaled)
            return@withContext scaled
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
    return@withContext null
}

@Composable
fun VideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit
) {
    var thumbnail by remember(videoPath) { mutableStateOf<Bitmap?>(ThumbnailCache.get(videoPath)) }

    LaunchedEffect(videoPath) {
        if (thumbnail == null) {
            val bmp = getVideoThumbnail(videoPath)
            if (bmp != null) {
                thumbnail = bmp
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = "Video Thumbnail",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        placeholder()
    }
}
