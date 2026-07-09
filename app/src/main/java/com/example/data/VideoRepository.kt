package com.example.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

class VideoRepository(private val dao: VideoPlayerDao) {

    private val okHttpClient = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Playlists
    val playlists: Flow<List<Playlist>> = dao.getAllPlaylists()

    suspend fun createPlaylist(title: String, description: String): Long {
        return dao.insertPlaylist(Playlist(title = title, description = description))
    }

    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylist(id)
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
                        val name = cursor.getString(nameColumn) ?: "Video-$id"
                        val path = cursor.getString(dataColumn)
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        
                        val rawResolution = if (resolutionColumn != -1) cursor.getString(resolutionColumn) else null
                        val parsedResolution = VideoModel.parseResolutionLabel(rawResolution)

                        videosList.add(
                            VideoModel(
                                id = id,
                                title = name,
                                urlOrPath = path,
                                duration = duration,
                                size = size,
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
}
