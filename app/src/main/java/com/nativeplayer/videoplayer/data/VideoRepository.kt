package com.nativeplayer.videoplayer.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

class VideoRepository(private val dao: VideoPlayerDao) {

    private val okHttpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val bookmarksMutex = Mutex()

    // Playlists
    val playlists: Flow<List<Playlist>> = dao.getAllPlaylists()

    suspend fun ensureDefaultBookmarksPlaylist() {
        withContext(Dispatchers.IO) {
            bookmarksMutex.withLock {
                val existing = dao.getBookmarksPlaylist()
                if (existing == null) {
                    dao.insertPlaylist(
                        Playlist(
                            title = "Bookmarks",
                            description = "Your bookmarked and favorite videos",
                            isSystem = true,
                            createdAt = Long.MAX_VALUE
                        )
                    )
                }
            }
        }
    }

    suspend fun createPlaylist(title: String, description: String): Long {
        return dao.insertPlaylist(Playlist(title = title, description = description))
    }

    suspend fun updatePlaylistThumbnail(id: Long, customThumbnailPath: String?) {
        withContext(Dispatchers.IO) {
            dao.updatePlaylistThumbnail(id, customThumbnailPath)
        }
    }

    suspend fun getBookmarksPlaylist(): Playlist? {
        return withContext(Dispatchers.IO) {
            dao.getBookmarksPlaylist()
        }
    }

    suspend fun deletePlaylist(id: Long) {
        withContext(Dispatchers.IO) {
            val playlist = dao.getPlaylistById(id)
            if (playlist != null && playlist.isSystem) {
                // System playlist (e.g. Bookmarks) cannot be deleted
                return@withContext
            }
            dao.deletePlaylist(id)
        }
    }

    // Playlist Items
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItem>> {
        return dao.getItemsForPlaylist(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: Long, title: String, urlOrPath: String, subtitleUrl: String? = null) {
        dao.insertPlaylistItem(
            PlaylistItem(
                playlistId = playlistId,
                title = title,
                urlOrPath = urlOrPath,
                subtitleUrl = subtitleUrl
            )
        )
    }

    suspend fun deletePlaylistItem(id: Long) {
        dao.deletePlaylistItem(id)
    }

    suspend fun getBookmarksItems(): List<PlaylistItem> {
        return withContext(Dispatchers.IO) {
            val bookmarks = dao.getBookmarksPlaylist() ?: return@withContext emptyList()
            dao.getPlaylistItemsDirect(bookmarks.id)
        }
    }

    suspend fun isVideoBookmarked(urlOrPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val bookmarks = dao.getBookmarksPlaylist() ?: return@withContext false
            val items = dao.getPlaylistItemsDirect(bookmarks.id)
            items.any { it.urlOrPath == urlOrPath }
        }
    }

    suspend fun addVideosToBookmarks(videos: List<VideoModel>): Int {
        return withContext(Dispatchers.IO) {
            ensureDefaultBookmarksPlaylist()
            val bookmarks = dao.getBookmarksPlaylist() ?: return@withContext 0
            val currentItems = dao.getPlaylistItemsDirect(bookmarks.id)
            val currentUrls = currentItems.map { it.urlOrPath }.toSet()

            var addedCount = 0
            videos.forEach { v ->
                if (!currentUrls.contains(v.urlOrPath)) {
                    dao.insertPlaylistItem(
                        PlaylistItem(
                            playlistId = bookmarks.id,
                            title = v.title,
                            urlOrPath = v.urlOrPath,
                            subtitleUrl = v.subtitleUrlOrPath,
                            duration = v.duration
                        )
                    )
                    addedCount++
                }
            }
            addedCount
        }
    }

    suspend fun removeVideosFromBookmarks(videos: List<VideoModel>): Int {
        return withContext(Dispatchers.IO) {
            val bookmarks = dao.getBookmarksPlaylist() ?: return@withContext 0
            val urls = videos.map { it.urlOrPath }
            dao.deletePlaylistItemsByUrls(bookmarks.id, urls)
            urls.size
        }
    }

    // Downloads
    val downloads: Flow<List<VideoDownload>> = dao.getAllDownloads()

    suspend fun deleteDownload(id: Long) {
        val download = dao.getDownloadById(id)
        if (download != null) {
            // Delete associated file
            val file = File(download.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            if (download.localSubtitlePath != null) {
                val subFile = File(download.localSubtitlePath)
                if (subFile.exists()) {
                    subFile.delete()
                }
            }
            dao.deleteDownload(id)
        }
    }

    suspend fun deleteDownloadByFilePath(filePath: String) {
        val download = dao.getDownloadByFilePath(filePath)
        if (download != null) {
            deleteDownload(download.id)
        } else {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun startDownload(
        context: Context,
        title: String,
        videoUrl: String,
        subtitleUrl: String? = null,
        destinationPath: String? = null
    ) {
        withContext(Dispatchers.IO) {
            // Check if already in progress
            val existing = dao.getDownloadByUrl(videoUrl)
            if (existing != null && (existing.downloadStatus == "DOWNLOADING" || existing.downloadStatus == "COMPLETED")) {
                return@withContext
            }

            val destFile = if (destinationPath != null) {
                File(destinationPath)
            } else {
                val filename = "vid_${System.currentTimeMillis()}.mp4"
                File(context.filesDir, filename)
            }

            val downloadId = dao.insertDownload(
                VideoDownload(
                    title = title,
                    sourceUrl = videoUrl,
                    localFilePath = destFile.absolutePath,
                    downloadStatus = "PENDING",
                    progress = 0f,
                    subtitleUrl = subtitleUrl
                )
            )

            // Start async download in repo coroutine scope
            scope.launch {
                executeDownload(context, downloadId, videoUrl, destFile, subtitleUrl)
            }
        }
    }

    private suspend fun executeDownload(
        context: Context,
        downloadId: Long,
        videoUrl: String,
        destFile: File,
        subtitleUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            try {
                var current = dao.getDownloadById(downloadId) ?: return@withContext
                dao.updateDownload(current.copy(downloadStatus = "DOWNLOADING"))

                // Optional Subtitle Download
                var localSubPath: String? = null
                if (!subtitleUrl.isNullOrEmpty()) {
                    try {
                        val subFilename = "sub_${System.currentTimeMillis()}.vtt"
                        val subFile = File(context.filesDir, subFilename)
                        val url = URL(subtitleUrl)
                        url.openStream().use { input ->
                            FileOutputStream(subFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        localSubPath = subFile.absolutePath
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "Failed to download subtitle: ${e.message}")
                    }
                }

                val request = Request.Builder().url(videoUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        current = dao.getDownloadById(downloadId) ?: return@withContext
                        dao.updateDownload(current.copy(downloadStatus = "FAILED"))
                        return@withContext
                    }

                    val body = response.body
                    if (body == null) {
                        current = dao.getDownloadById(downloadId) ?: return@withContext
                        dao.updateDownload(current.copy(downloadStatus = "FAILED"))
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    var bytesRead: Long = 0
                    val buffer = ByteArray(8192)
                    var lastUpdate = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 500) { // Update progress at most every 500ms
                                    lastUpdate = now
                                    val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                                    current = dao.getDownloadById(downloadId) ?: break
                                    dao.updateDownload(
                                        current.copy(
                                            downloadStatus = "DOWNLOADING",
                                            progress = progress,
                                            totalSize = totalBytes,
                                            downloadedSize = bytesRead
                                        )
                                    )
                                }
                                read = input.read(buffer)
                            }
                        }
                    }

                    current = dao.getDownloadById(downloadId) ?: return@withContext
                    dao.updateDownload(
                        current.copy(
                            downloadStatus = "COMPLETED",
                            progress = 1.0f,
                            totalSize = totalBytes,
                            downloadedSize = totalBytes,
                            localSubtitlePath = localSubPath
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Download failed: ${e.message}", e)
                val current = dao.getDownloadById(downloadId) ?: return@withContext
                dao.updateDownload(current.copy(downloadStatus = "FAILED"))
            }
        }
    }

    // Scan local videos from MediaStore
    suspend fun getLocalVideos(context: Context): List<VideoModel> {
        return withContext(Dispatchers.IO) {
            val videosList = mutableListOf<VideoModel>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.RESOLUTION
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val resolutionColumn = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val rawName = cursor.getString(nameColumn) ?: ""
                        val path = cursor.getString(dataColumn) ?: continue
                        val file = File(path)
                        if (!file.exists()) continue

                        val name = if (rawName.isBlank() || rawName.startsWith("Video-")) file.name else rawName
                        val size = cursor.getLong(sizeColumn)
                        val actualSize = if (size <= 0L) file.length() else size

                        var actualDuration = cursor.getLong(durationColumn)
                        if (actualDuration <= 0L) {
                            val retriever = android.media.MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(path)
                                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                actualDuration = durStr?.toLongOrNull() ?: 0L
                            } catch (_: Exception) {
                            } finally {
                                try { retriever.release() } catch (_: Exception) {}
                            }
                        }

                        val rawResolution = if (resolutionColumn != -1) cursor.getString(resolutionColumn) else null
                        val parsedResolution = VideoModel.parseResolutionLabel(rawResolution)

                        videosList.add(
                            VideoModel(
                                id = id,
                                title = name,
                                urlOrPath = path,
                                duration = actualDuration,
                                size = actualSize,
                                isLocal = true,
                                resolution = parsedResolution
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoRepository", "Failed to query media store: ${e.message}")
            }
            videosList
        }
    }

    // Single Video File Operations
    suspend fun renameVideoFile(context: Context, video: VideoModel, newNameRaw: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val oldFile = File(video.urlOrPath)
                if (!oldFile.exists()) {
                    return@withContext Result.failure(Exception("File does not exist"))
                }
                
                val ext = oldFile.extension
                var newName = newNameRaw.trim()
                if (ext.isNotEmpty() && !newName.endsWith(".$ext", ignoreCase = true)) {
                    newName = "$newName.$ext"
                }

                val parentDir = oldFile.parentFile ?: return@withContext Result.failure(Exception("Invalid directory"))
                val newFile = File(parentDir, newName)

                // If unchanged, treat as success immediately
                if (newFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = false)) {
                    return@withContext Result.success(oldFile.absolutePath)
                }

                if (newFile.exists() && !newFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = true)) {
                    return@withContext Result.failure(Exception("A file with this name already exists"))
                }

                var success = false
                try {
                    success = oldFile.renameTo(newFile)
                } catch (e: Exception) {
                    Log.e("VideoRepository", "renameTo failed: ${e.message}")
                }

                // If direct rename failed, try copy + delete fallback if storage permits
                if (!success && oldFile.exists() && !newFile.exists()) {
                    try {
                        oldFile.copyTo(newFile, overwrite = false)
                        if (newFile.exists() && newFile.length() == oldFile.length()) {
                            oldFile.delete()
                            purgeFileFromMediaStore(context, oldFile)
                            success = true
                        }
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "Copy-delete fallback failed: ${e.message}")
                    }
                }

                // If still not renamed, attempt MediaStore ContentResolver update
                if (!success) {
                    try {
                        val projection = arrayOf(MediaStore.Video.Media._ID)
                        val selection = "${MediaStore.Video.Media.DATA} = ?"
                        val selectionArgs = arrayOf(oldFile.absolutePath)
                        context.contentResolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                                val contentUri = android.content.ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    id
                                )
                                val values = android.content.ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                                    put(MediaStore.Video.Media.TITLE, newName.substringBeforeLast('.'))
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        put(MediaStore.Video.Media.DATA, newFile.absolutePath)
                                    }
                                }
                                val updatedRows = context.contentResolver.update(contentUri, values, null, null)
                                if (updatedRows > 0) {
                                    success = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "MediaStore update failed: ${e.message}")
                    }
                }

                if (success || newFile.exists()) {
                    deleteDownloadByFilePath(oldFile.absolutePath)
                    purgeFileFromMediaStore(context, oldFile)
                    // Update MediaStore for the new file
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(oldFile.absolutePath, newFile.absolutePath),
                        null,
                        null
                    )
                    Result.success(newFile.absolutePath)
                } else {
                    Result.failure(Exception("Failed to rename file. Please check storage permissions."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteVideoFile(context: Context, video: VideoModel): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(video.urlOrPath)
                var deleted = false
                if (file.exists()) {
                    deleted = file.delete()
                }

                // Delete from downloads database if present
                deleteDownloadByFilePath(video.urlOrPath)

                // Rescan / remove from MediaStore
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(video.urlOrPath),
                    null,
                    null
                )

                Result.success(deleted)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun copyVideoFile(context: Context, video: VideoModel, targetDirPath: String): Result<String> {
        return copyVideoFileWithProgress(context, video, targetDirPath) { _, _ -> }
    }

    suspend fun moveVideoFile(context: Context, video: VideoModel, targetDirPath: String): Result<String> {
        return moveVideoFileWithProgress(context, video, targetDirPath) { _, _ -> }
    }

    private fun purgeFileFromMediaStore(context: Context, file: File) {
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val contentUri = android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    context.contentResolver.delete(contentUri, null, null)
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Video.Media.DATA}=?",
                arrayOf(file.absolutePath)
            )
        } catch (_: Exception) {}
    }

    suspend fun copyVideoFileWithProgress(
        context: Context,
        video: VideoModel,
        targetDirPath: String,
        overwrite: Boolean = false,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val srcFile = File(video.urlOrPath)
                if (!srcFile.exists()) {
                    return@withContext Result.failure(Exception("Source file does not exist"))
                }

                val targetDir = File(targetDirPath)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                var destFile = File(targetDir, srcFile.name)
                if (destFile.exists()) {
                    if (overwrite) {
                        destFile.delete()
                        purgeFileFromMediaStore(context, destFile)
                    } else {
                        val nameWithoutExt = srcFile.nameWithoutExtension
                        val ext = srcFile.extension
                        val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_copy.$ext" else "${nameWithoutExt}_copy"
                        destFile = File(targetDir, newName)
                    }
                }

                val totalBytes = srcFile.length()
                var bytesTransferred = 0L
                val buffer = ByteArray(64 * 1024)
                var lastReportTime = 0L

                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            bytesTransferred += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 100 || bytesTransferred == totalBytes) {
                                lastReportTime = now
                                onProgress(bytesTransferred, totalBytes)
                            }
                            read = input.read(buffer)
                        }
                    }
                }

                scanFileSuspend(context, destFile.absolutePath)
                Result.success(destFile.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun scanFileSuspend(context: Context, path: String): String? {
        return kotlin.coroutines.suspendCoroutine { cont ->
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    null
                ) { scannedPath, _ ->
                    cont.resumeWith(Result.success(scannedPath))
                }
            } catch (e: Exception) {
                cont.resumeWith(Result.success(null))
            }
        }
    }

    suspend fun moveVideoFileWithProgress(
        context: Context,
        video: VideoModel,
        targetDirPath: String,
        overwrite: Boolean = false,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val srcFile = File(video.urlOrPath)
                if (!srcFile.exists()) {
                    return@withContext Result.failure(Exception("Source file does not exist"))
                }

                val targetDir = File(targetDirPath)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                var destFile = File(targetDir, srcFile.name)
                if (destFile.exists()) {
                    if (overwrite) {
                        destFile.delete()
                        purgeFileFromMediaStore(context, destFile)
                    } else {
                        val nameWithoutExt = srcFile.nameWithoutExtension
                        val ext = srcFile.extension
                        val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_moved.$ext" else "${nameWithoutExt}_moved"
                        destFile = File(targetDir, newName)
                    }
                }

                val totalBytes = srcFile.length()

                // Try atomic OS rename first (moves file in 1ms on same storage volume & guarantees removal from source folder)
                val movedAtomically = try { srcFile.renameTo(destFile) } catch (_: Exception) { false }
                if (movedAtomically) {
                    onProgress(totalBytes, totalBytes)
                    purgeFileFromMediaStore(context, srcFile)
                    scanFileSuspend(context, srcFile.absolutePath)
                    scanFileSuspend(context, destFile.absolutePath)
                    return@withContext Result.success(destFile.absolutePath)
                }

                // Fallback for cross-volume moves: Stream copy with real-time progress update
                var bytesTransferred = 0L
                val buffer = ByteArray(64 * 1024)
                var lastReportTime = 0L

                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            bytesTransferred += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 100 || bytesTransferred == totalBytes) {
                                lastReportTime = now
                                onProgress(bytesTransferred, totalBytes)
                            }
                            read = input.read(buffer)
                        }
                    }
                }

                // Verify destination file created cleanly
                if (destFile.exists() && (totalBytes == 0L || destFile.length() >= totalBytes)) {
                    // Remove source file from storage
                    if (srcFile.exists()) {
                        var deleted = srcFile.delete()
                        if (!deleted) {
                            try {
                                val fos = java.io.FileOutputStream(srcFile)
                                fos.channel.truncate(0)
                                fos.close()
                            } catch (_: Exception) {}
                            deleted = srcFile.delete()
                        }
                    }

                    // Remove source record from MediaStore ContentResolver
                    purgeFileFromMediaStore(context, srcFile)

                    // Await MediaScanner indexing for both source & destination
                    scanFileSuspend(context, srcFile.absolutePath)
                    scanFileSuspend(context, destFile.absolutePath)

                    Result.success(destFile.absolutePath)
                } else {
                    Result.failure(Exception("Failed to move file"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun copyMultipleVideosWithProgress(
        context: Context,
        videos: List<VideoModel>,
        targetDirPath: String,
        overwrite: Boolean = false,
        onProgress: (overallTransferred: Long, grandTotal: Long, currentFileName: String) -> Unit
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val grandTotal = videos.fold(0L) { acc, v -> acc + File(v.urlOrPath).length() }
                var overallTransferred = 0L
                var successCount = 0

                for (video in videos) {
                    val res = copyVideoFileWithProgress(
                        context,
                        video,
                        targetDirPath,
                        overwrite
                    ) { transferredInFile, fileTotal ->
                        onProgress(overallTransferred + transferredInFile, grandTotal, video.title)
                    }

                    if (res.isSuccess) {
                        successCount++
                        overallTransferred += File(video.urlOrPath).length()
                    }
                }

                Result.success(successCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun moveMultipleVideosWithProgress(
        context: Context,
        videos: List<VideoModel>,
        targetDirPath: String,
        overwrite: Boolean = false,
        onProgress: (overallTransferred: Long, grandTotal: Long, currentFileName: String) -> Unit
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val grandTotal = videos.fold(0L) { acc, v -> acc + File(v.urlOrPath).length() }
                var overallTransferred = 0L
                var successCount = 0

                for (video in videos) {
                    val fileLength = File(video.urlOrPath).length()
                    val res = moveVideoFileWithProgress(
                        context,
                        video,
                        targetDirPath,
                        overwrite
                    ) { transferredInFile, fileTotal ->
                        onProgress(overallTransferred + transferredInFile, grandTotal, video.title)
                    }

                    if (res.isSuccess) {
                        successCount++
                        overallTransferred += fileLength
                    }
                }

                Result.success(successCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
