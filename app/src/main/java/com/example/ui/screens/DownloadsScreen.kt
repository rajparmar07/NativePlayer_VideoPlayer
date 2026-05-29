package com.example.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VideoDownload
import com.example.data.VideoModel
import com.example.viewmodel.VideoPlayerViewModel

@Composable
fun DownloadsScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("downloads_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Column {
                Text(
                    text = "Downloads",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Offline local video database copy cache",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Downloads Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloaded streams or server links will appear here, fully viewable offline.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(downloads) { download ->
                        DownloadItemCard(
                            download = download,
                            onClick = {
                                if (download.downloadStatus == "COMPLETED") {
                                    val video = VideoModel(
                                        id = "download_${download.id}",
                                        title = download.title,
                                        urlOrPath = download.localFilePath,
                                        subtitleUrlOrPath = download.localSubtitlePath,
                                        isOffline = true
                                    )
                                    viewModel.playVideo(video)
                                    onNavigateToPlayer()
                                }
                            },
                            onDelete = {
                                viewModel.deleteDownload(download.id)
                            }
                        )
                    }
                }
            }
        }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
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

                    Column {
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
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                }

                IconButton(onClick = onDelete) {
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
