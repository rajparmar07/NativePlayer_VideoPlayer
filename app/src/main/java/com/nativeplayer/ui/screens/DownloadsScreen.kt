package com.nativeplayer.ui.screens

import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativeplayer.R
import com.nativeplayer.data.VideoDownload
import com.nativeplayer.data.VideoModel
import com.nativeplayer.ui.components.VideoThumbnail
import com.nativeplayer.ui.components.rememberVideoResolution
import com.nativeplayer.viewmodel.VideoPlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permissions State
    val permissionType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission = permissionType)

    // Form and validation states
    var urlInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // List of completed downloads in directory
    val completedFiles = remember { mutableStateListOf<File>() }

    // Helper to refresh completed downloads
    val refreshFiles = {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appName = context.getString(R.string.app_name)
        val appDownloadDir = File(downloadDir, appName)
        if (appDownloadDir.exists() && appDownloadDir.isDirectory) {
            val files = appDownloadDir.listFiles { file ->
                file.isFile && (
                    file.name.endsWith(".mp4", ignoreCase = true) ||
                    file.name.endsWith(".mkv", ignoreCase = true) ||
                    file.name.endsWith(".webm", ignoreCase = true) ||
                    file.name.endsWith(".avi", ignoreCase = true) ||
                    file.name.endsWith(".3gp", ignoreCase = true) ||
                    file.name.endsWith(".mov", ignoreCase = true)
                )
            }?.toList() ?: emptyList()
            completedFiles.clear()
            completedFiles.addAll(files.sortedByDescending { it.lastModified() })
        } else {
            completedFiles.clear()
        }
    }

    // Refresh files list when permission is granted
    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            refreshFiles()
        }
    }

    // Also observe database downloads to refresh the files list when any download completes
    LaunchedEffect(downloads) {
        if (permissionState.status.isGranted) {
            refreshFiles()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("downloads_screen_root")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 104.dp + navBarBottom),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Minimal Sleek Form Design (Directly on the background, card-free)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Download link / URL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                errorMessage = null
                                successMessage = null
                            },
                            placeholder = { Text("Paste direct video link (HTTP/HTTPS)...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("download_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Error/Success messages
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("download_error_msg")
                            )
                        }
                        if (successMessage != null) {
                            Text(
                                text = successMessage!!,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("download_success_msg")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Sleek Action Button
                        Button(
                            onClick = {
                                if (urlInput.isBlank()) {
                                    errorMessage = "Please enter a stream link"
                                    return@Button
                                }
                                errorMessage = null
                                successMessage = null

                                isValidating = true
                                coroutineScope.launch {
                                    val validation = validateVideoLink(urlInput)
                                    isValidating = false
                                    when (validation) {
                                        is ValidationResult.Success -> {
                                            if (!permissionState.status.isGranted) {
                                                permissionState.launchPermissionRequest()
                                                return@launch
                                            }

                                            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            val appName = context.getString(R.string.app_name)
                                            val appDownloadDir = File(downloadDir, appName)
                                            if (!appDownloadDir.exists()) {
                                                appDownloadDir.mkdirs()
                                            }
                                            val fileName = getFileNameFromUrl(urlInput, validation.contentDisposition)
                                            val destFile = getUniqueFile(appDownloadDir, fileName)
                                            val title = fileName.substringBeforeLast(".")

                                            viewModel.startDownload(
                                                context = context,
                                                title = title,
                                                videoUrl = urlInput,
                                                subtitleUrl = null,
                                                destinationPath = destFile.absolutePath
                                            )
                                            urlInput = ""
                                            successMessage = "Download started successfully!"
                                            delay(500)
                                            refreshFiles()
                                        }
                                        is ValidationResult.Error -> {
                                            errorMessage = validation.message
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("download_action_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isValidating
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating Link...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Video", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section 2: Active downloads (downloads in progress / pending)
                val activeDownloads = downloads.filter { it.downloadStatus == "DOWNLOADING" || it.downloadStatus == "PENDING" || it.downloadStatus == "FAILED" }
                if (activeDownloads.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Downloads",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(activeDownloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            onClick = {},
                            onDelete = { viewModel.deleteDownload(download.id) }
                        )
                    }
                }

                // Section 3: Completed Saved Videos List
                item {
                    Text(
                        text = "Saved Videos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (!permissionState.status.isGranted) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Storage Permission Required",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "To scan, list, and play video files downloaded to the system Download directory, storage permissions are required.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { permissionState.launchPermissionRequest() },
                                    modifier = Modifier.testTag("grant_permission_downloads_button")
                                ) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                } else if (completedFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OfflinePin,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "No Downloaded Videos",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Downloaded videos will appear here and can be played offline.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(completedFiles, key = { it.absolutePath }) { file ->
                        SavedVideoItem(
                            file = file,
                            onPlay = {
                                val video = VideoModel(
                                    id = "local_download_${file.name}",
                                    title = file.nameWithoutExtension,
                                    urlOrPath = file.absolutePath,
                                    isOffline = true
                                )
                                viewModel.playVideo(video)
                                onNavigateToPlayer()
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    viewModel.deleteDownloadByFilePath(file.absolutePath)
                                    delay(200)
                                    refreshFiles()
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedVideoItem(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    val extension = remember(file) { file.extension.uppercase().ifEmpty { "VID" } }
    val displayPath = remember(file) {
        file.parentFile?.absolutePath ?: "/storage/emulated/0/Download/Native Player"
    }
    val cleanTitle = remember(file) { file.nameWithoutExtension }

    // Asynchronously fetch video duration and resolution
    val duration = rememberVideoDuration(file.absolutePath)
    val videoModel = remember(file) {
        VideoModel(
            id = "local_download_${file.name}",
            title = file.nameWithoutExtension,
            urlOrPath = file.absolutePath,
            isOffline = true
        )
    }
    val resolution = rememberVideoResolution(videoModel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag("saved_video_item_${file.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Media thumbnail (120.dp x 80.dp matching LocalVideoCard)
        Box(
            modifier = Modifier
                .size(120.dp, 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            VideoThumbnail(
                videoPath = file.absolutePath,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )

            // Resolution badge (top-left)
            if (!resolution.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = resolution,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        lineHeight = 7.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and details column
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Video Title
            Text(
                text = cleanTitle,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Video Path
            Text(
                text = displayPath,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Extension badge, duration, size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Extension format badge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = extension,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        lineHeight = 7.sp
                    )
                }

                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = formatDuration(duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = formatSize(file.length()),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        // Delete Button (specific to Saved Videos screen)
        IconButton(
            onClick = { showConfirmDelete = true },
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .testTag("delete_saved_video_${file.name}")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete downloaded video",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Video?") },
            text = { Text("Are you sure you want to permanently delete '${file.name}' from your device storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DownloadItemCard(
    download: VideoDownload,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = download.downloadStatus == "COMPLETED") { onClick() }
            .testTag("download_card_${download.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (download.downloadStatus) {
                                    "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
                                    "DOWNLOADING" -> MaterialTheme.colorScheme.secondaryContainer
                                    "FAILED" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (download.downloadStatus) {
                                "COMPLETED" -> Icons.Default.CheckCircle
                                "DOWNLOADING" -> Icons.Default.FileDownload
                                "FAILED" -> Icons.Default.Error
                                else -> Icons.Default.Pending
                            },
                            contentDescription = null,
                            tint = when (download.downloadStatus) {
                                "COMPLETED" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "DOWNLOADING" -> MaterialTheme.colorScheme.onSecondaryContainer
                                "FAILED" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (download.downloadStatus == "COMPLETED") "Offline copy ready" else download.sourceUrl,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(top = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete download",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Progress Indicators for downloading
            if (download.downloadStatus == "DOWNLOADING") {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(download.progress * 100).toInt()}% Done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${formatSize(download.downloadedSize)} / ${formatSize(download.totalSize)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else if (download.downloadStatus == "COMPLETED" && download.totalSize > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File size: ${formatSize(download.totalSize)} • Click item to play offline",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else if (download.downloadStatus == "FAILED") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to download this video. Please check your internet connection.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun rememberVideoDuration(videoPath: String): Long {
    var duration by remember(videoPath) { mutableLongStateOf(0L) }
    LaunchedEffect(videoPath) {
        withContext(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val dur = durationStr?.toLongOrNull() ?: 0L
                duration = dur
            } catch (e: Exception) {
                // Ignore
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
    return duration
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private suspend fun validateVideoLink(url: String): ValidationResult {
    return withContext(Dispatchers.IO) {
        try {
            val parsedUrl = try {
                URL(url)
            } catch (e: Exception) {
                return@withContext ValidationResult.Error("Invalid URL format. Please enter a valid HTTP/HTTPS link.")
            }

            if (parsedUrl.protocol != "http" && parsedUrl.protocol != "https") {
                return@withContext ValidationResult.Error("Only HTTP and HTTPS links are supported.")
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val headRequest = Request.Builder()
                .url(url)
                .head()
                .build()

            var contentType: String? = null
            var contentDisposition: String? = null

            try {
                client.newCall(headRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        contentType = response.header("Content-Type")
                        contentDisposition = response.header("Content-Disposition")
                    }
                }
            } catch (e: Exception) {
                // Ignore HEAD error, fallback to GET
            }

            if (contentType == null) {
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1024")
                    .build()
                try {
                    client.newCall(getRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            contentType = response.header("Content-Type")
                            contentDisposition = response.header("Content-Disposition")
                        } else {
                            return@withContext ValidationResult.Error("Server returned error: HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    return@withContext ValidationResult.Error("Unable to connect to the link. Please check your network connection.")
                }
            }

            val mimeType = contentType
            if (mimeType == null) {
                return@withContext ValidationResult.Error("Unable to determine the file type from the link.")
            }

            if (mimeType.startsWith("video/", ignoreCase = true) ||
                mimeType.contains("application/x-mpegurl", ignoreCase = true) ||
                mimeType.contains("application/vnd.apple.mpegurl", ignoreCase = true)
            ) {
                ValidationResult.Success(mimeType, contentDisposition)
            } else {
                ValidationResult.Error("The link points to a non-video file (${mimeType}). Only video files are allowed.")
            }
        } catch (e: java.net.UnknownHostException) {
            ValidationResult.Error("Unable to resolve host. Please check your internet connection.")
        } catch (e: java.net.ConnectException) {
            ValidationResult.Error("Connection timed out. The server might be offline.")
        } catch (e: Exception) {
            ValidationResult.Error("Validation failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}

private sealed class ValidationResult {
    data class Success(val contentType: String, val contentDisposition: String?) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

private fun getFileNameFromUrl(url: String, contentDisposition: String?): String {
    if (!contentDisposition.isNullOrEmpty()) {
        val matches = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition)
        if (matches != null && matches.groupValues.size > 1) {
            return matches.groupValues[1]
        }
    }
    try {
        val path = URL(url).path
        val name = path.substring(path.lastIndexOf('/') + 1)
        if (name.isNotEmpty()) {
            val hasVideoExt = name.endsWith(".mp4", ignoreCase = true) ||
                    name.endsWith(".mkv", ignoreCase = true) ||
                    name.endsWith(".webm", ignoreCase = true) ||
                    name.endsWith(".avi", ignoreCase = true) ||
                    name.endsWith(".3gp", ignoreCase = true) ||
                    name.endsWith(".mov", ignoreCase = true)
            if (hasVideoExt) {
                return name
            }
        }
    } catch (e: Exception) {}
    return "video_${System.currentTimeMillis()}.mp4"
}

private fun getUniqueFile(directory: File, filename: String): File {
    var file = File(directory, filename)
    if (!file.exists()) return file
    val baseName = filename.substringBeforeLast(".")
    val extension = filename.substringAfterLast(".", "")
    val extSuffix = if (extension.isNotEmpty()) ".$extension" else ""
    var counter = 1
    while (file.exists()) {
        file = File(directory, "$baseName ($counter)$extSuffix")
        counter++
    }
    return file
}
