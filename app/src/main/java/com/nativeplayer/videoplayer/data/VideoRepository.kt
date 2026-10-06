package com.nativeplayer.videoplayer.data

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.webkit.MimeTypeMap
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
                MediaStore.Video.Media.RESOLUTION,
                MediaStore.Video.Media.DATE_MODIFIED
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
                    val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val rawName = cursor.getString(nameColumn) ?: ""
                        val path = cursor.getString(dataColumn) ?: continue
                        val file = File(path)
                        if (!file.exists()) {
                            // Video was deleted externally: notify MediaScanner in background to prune stale MediaStore row
                            try {
                                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                            } catch (_: Exception) {}
                            continue
                        }

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

                        val dateSec = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                        val actualDateModified = if (dateSec > 0L) dateSec * 1000L else file.lastModified()

                        videosList.add(
                            VideoModel(
                                id = id,
                                title = name,
                                urlOrPath = path,
                                duration = actualDuration,
                                size = actualSize,
                                isLocal = true,
                                resolution = parsedResolution,
                                dateModified = actualDateModified
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

    /**
     * Inspects the folder currently open in the UI and indexes any new video files
     * that were placed into it externally but not yet processed by MediaStore.
     */
    fun scanDirectoryForNewVideos(
        context: Context,
        folderPath: String,
        knownPaths: Set<String>,
        onScanned: () -> Unit
    ) {
        try {
            val dir = File(folderPath)
            if (!dir.exists() || !dir.isDirectory) return
            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v", "wmv")
            val diskFiles = dir.listFiles { f -> f.isFile && f.extension.lowercase() in videoExtensions } ?: return
            val missingFromMediaStore = diskFiles.filter { it.absolutePath !in knownPaths }
            if (missingFromMediaStore.isNotEmpty()) {
                val pathsToScan = missingFromMediaStore.map { it.absolutePath }.toTypedArray()
                MediaScannerConnection.scanFile(context, pathsToScan, null) { _, _ ->
                    onScanned()
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error scanning directory for new videos: ${e.message}")
        }
    }

    // MediaStore & Scoped Storage Helpers
    fun getMediaUriForVideo(context: Context, video: VideoModel): Uri? {
        val idLong = video.id.toLongOrNull()
        if (idLong != null && idLong > 0) {
            return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
        }
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(video.urlOrPath)
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getRelativePathForFolder(folderPath: String): String {
        val clean = folderPath.replace('\\', '/')
        val storageRoots = listOf(
            Environment.getExternalStorageDirectory().absolutePath.replace('\\', '/'),
            "/storage/emulated/0"
        )
        var rel = clean
        for (root in storageRoots) {
            if (rel.startsWith(root, ignoreCase = true)) {
                rel = rel.substring(root.length).trimStart('/')
                break
            }
        }
        if (rel.startsWith("storage/")) {
            val parts = rel.split('/')
            if (parts.size >= 2) {
                rel = parts.drop(2).joinToString("/")
            }
        }
        if (rel.isBlank() || rel == "/") {
            rel = Environment.DIRECTORY_MOVIES
        }
        return if (rel.endsWith("/")) rel else "$rel/"
    }

    fun getMimeType(filePath: String): String {
        val ext = File(filePath).extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
    }

    fun checkFileExistsInFolder(context: Context, targetDirPath: String, fileName: String): Boolean {
        val directFile = File(targetDirPath, fileName)
        if (directFile.exists()) return true
        val relPath = getRelativePathForFolder(targetDirPath)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relPath)
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                return cursor.count > 0
            }
        } catch (_: Exception) {}
        return false
    }

    // Single Video File Operations
    suspend fun renameVideoFile(context: Context, video: VideoModel, newNameRaw: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val oldFile = File(video.urlOrPath)
                val ext = oldFile.extension.ifEmpty { "mp4" }
                var newName = newNameRaw.trim()
                if (ext.isNotEmpty() && !newName.endsWith(".$ext", ignoreCase = true)) {
                    newName = "$newName.$ext"
                }

                val titleWithoutExt = newName.substringBeforeLast('.')
                val contentUri = getMediaUriForVideo(context, video)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                        put(MediaStore.Video.Media.TITLE, titleWithoutExt)
                    }
                    val updatedRows = try {
                        context.contentResolver.update(contentUri, values, null, null)
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "ContentResolver.update failed: ${e.message}")
                        0
                    }
                    if (updatedRows > 0) {
                        var newPath = File(oldFile.parentFile, newName).absolutePath
                        try {
                            context.contentResolver.query(
                                contentUri,
                                arrayOf(MediaStore.Video.Media.DATA),
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    newPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: newPath
                                }
                            }
                        } catch (_: Exception) {}
                        deleteDownloadByFilePath(oldFile.absolutePath)
                        return@withContext Result.success(newPath)
                    }
                }

                // Fallback for API < 29 or direct file rename
                val parentDir = oldFile.parentFile ?: return@withContext Result.failure(Exception("Invalid directory"))
                val newFile = File(parentDir, newName)

                if (newFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = false)) {
                    return@withContext Result.success(oldFile.absolutePath)
                }

                if (newFile.exists() && !newFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = true)) {
                    return@withContext Result.failure(Exception("A file with this name already exists"))
                }

                var success = try { oldFile.renameTo(newFile) } catch (_: Exception) { false }
                if (success || newFile.exists()) {
                    deleteDownloadByFilePath(oldFile.absolutePath)
                    purgeFileFromMediaStore(context, oldFile)
                    scanFileSuspend(context, newFile.absolutePath)
                    return@withContext Result.success(newFile.absolutePath)
                }

                Result.failure(Exception("Failed to rename video"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteVideoFile(context: Context, video: VideoModel): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val contentUri = getMediaUriForVideo(context, video)
                var deleted = false
                if (contentUri != null) {
                    val rows = try {
                        context.contentResolver.delete(contentUri, null, null)
                    } catch (_: Exception) { 0 }
                    deleted = rows > 0
                }
                val file = File(video.urlOrPath)
                if (file.exists()) {
                    deleted = file.delete() || deleted
                }
                deleteDownloadByFilePath(video.urlOrPath)
                purgeFileFromMediaStore(context, file)
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
                    val contentUri = ContentUris.withAppendedId(
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
                val srcUri = getMediaUriForVideo(context, video)
                val totalBytes = if (video.size > 0) video.size else if (srcFile.exists()) srcFile.length() else 0L
                val relativePath = getRelativePathForFolder(targetDirPath)
                val mimeType = getMimeType(video.urlOrPath)

                var destFileName = srcFile.name
                if (!overwrite) {
                    val existsInTarget = checkFileExistsInFolder(context, targetDirPath, destFileName)
                    if (existsInTarget) {
                        val nameWithoutExt = srcFile.nameWithoutExtension
                        val ext = srcFile.extension
                        destFileName = if (ext.isNotEmpty()) "${nameWithoutExt}_copy.$ext" else "${nameWithoutExt}_copy"
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, destFileName)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    val destUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: return@withContext Result.failure(Exception("Failed to create destination file in MediaStore"))

                    var bytesTransferred = 0L
                    val buffer = ByteArray(64 * 1024)
                    var lastReportTime = 0L

                    val inputStream = if (srcFile.exists() && srcFile.canRead()) {
                        srcFile.inputStream()
                    } else if (srcUri != null) {
                        context.contentResolver.openInputStream(srcUri)
                    } else null

                    if (inputStream == null) {
                        context.contentResolver.delete(destUri, null, null)
                        return@withContext Result.failure(Exception("Cannot read source video"))
                    }

                    inputStream.use { input ->
                        val outputStream = context.contentResolver.openOutputStream(destUri)
                            ?: run {
                                context.contentResolver.delete(destUri, null, null)
                                return@withContext Result.failure(Exception("Cannot open destination output stream"))
                            }
                        outputStream.use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                bytesTransferred += read
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 100 || (totalBytes > 0 && bytesTransferred >= totalBytes)) {
                                    lastReportTime = now
                                    onProgress(bytesTransferred, totalBytes)
                                }
                                read = input.read(buffer)
                            }
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(destUri, contentValues, null, null)

                    var finalPath = File(targetDirPath, destFileName).absolutePath
                    try {
                        context.contentResolver.query(destUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                finalPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: finalPath
                            }
                        }
                    } catch (_: Exception) {}

                    scanFileSuspend(context, finalPath)
                    return@withContext Result.success(finalPath)
                } else {
                    // Pre-Android 10
                    val targetDir = File(targetDirPath)
                    if (!targetDir.exists()) targetDir.mkdirs()
                    var destFile = File(targetDir, destFileName)

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
                                if (now - lastReportTime > 100 || (totalBytes > 0 && bytesTransferred >= totalBytes)) {
                                    lastReportTime = now
                                    onProgress(bytesTransferred, totalBytes)
                                }
                                read = input.read(buffer)
                            }
                        }
                    }
                    scanFileSuspend(context, destFile.absolutePath)
                    return@withContext Result.success(destFile.absolutePath)
                }
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
                val contentUri = getMediaUriForVideo(context, video)
                val totalBytes = if (video.size > 0) video.size else if (srcFile.exists()) srcFile.length() else 0L
                val targetRelativePath = getRelativePathForFolder(targetDirPath)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && contentUri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.RELATIVE_PATH, targetRelativePath)
                    }
                    val updatedRows = try {
                        context.contentResolver.update(contentUri, values, null, null)
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "RELATIVE_PATH update failed: ${e.message}")
                        0
                    }
                    if (updatedRows > 0) {
                        onProgress(totalBytes, totalBytes)
                        var newPath = File(targetDirPath, srcFile.name).absolutePath
                        try {
                            context.contentResolver.query(contentUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    newPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: newPath
                                }
                            }
                        } catch (_: Exception) {}
                        deleteDownloadByFilePath(srcFile.absolutePath)
                        scanFileSuspend(context, newPath)
                        return@withContext Result.success(newPath)
                    }
                }

                // Fallback 1: Atomic OS rename (same volume or pre-Q)
                val targetDir = File(targetDirPath)
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, srcFile.name)
                val movedAtomically = try { srcFile.renameTo(destFile) } catch (_: Exception) { false }
                if (movedAtomically) {
                    onProgress(totalBytes, totalBytes)
                    purgeFileFromMediaStore(context, srcFile)
                    scanFileSuspend(context, srcFile.absolutePath)
                    scanFileSuspend(context, destFile.absolutePath)
                    return@withContext Result.success(destFile.absolutePath)
                }

                // Fallback 2: MediaStore stream copy + delete source
                val copyResult = copyVideoFileWithProgress(context, video, targetDirPath, overwrite, onProgress)
                if (copyResult.isSuccess) {
                    deleteVideoFile(context, video)
                    return@withContext copyResult
                }

                Result.failure(Exception("Failed to move video"))
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
