package com.nativeplayer.videoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativeplayer.videoplayer.data.VideoModel
import java.io.File
import android.os.Environment

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class VideoActionType {
    RENAME,
    FILE_INFO,
    COPY,
    MOVE,
    DELETE
}

@Composable
fun VideoActionsBottomSheet(
    video: VideoModel,
    onDismissRequest: () -> Unit,
    onActionSelected: (VideoActionType) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismissRequest() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 12.dp)
                        .testTag("video_actions_bottom_sheet")
                ) {
                    // Drag handle indicator
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Video title & path preview header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.urlOrPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    // Action Items strictly in specified order:
                    // 1. Rename
                    // 2. File Info
                    // 3. Copy
                    // 4. Cut
                    // 5. Move
                    // 6. Delete (last, destructive)
                    ActionMenuItem(
                        icon = Icons.Default.DriveFileRenameOutline,
                        title = "Rename",
                        subtitle = "Rename video file",
                        onClick = {
                            onActionSelected(VideoActionType.RENAME)
                        },
                        testTag = "action_rename"
                    )

                    ActionMenuItem(
                        icon = Icons.Default.Info,
                        title = "File Info",
                        subtitle = "View codec, audio & subtitle metadata",
                        onClick = {
                            onActionSelected(VideoActionType.FILE_INFO)
                        },
                        testTag = "action_file_info"
                    )

                    ActionMenuItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy",
                        subtitle = "Copy file to another folder",
                        onClick = {
                            onActionSelected(VideoActionType.COPY)
                        },
                        testTag = "action_copy"
                    )

                    ActionMenuItem(
                        icon = Icons.Default.DriveFileMove,
                        title = "Move",
                        subtitle = "Move file to another folder",
                        onClick = {
                            onActionSelected(VideoActionType.MOVE)
                        },
                        testTag = "action_move"
                    )

                    ActionMenuItem(
                        icon = Icons.Default.Delete,
                        title = "Delete from storage",
                        subtitle = "Permanently remove file from device",
                        isDestructive = true,
                        onClick = {
                            onActionSelected(VideoActionType.DELETE)
                        },
                        testTag = "action_delete"
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val tintColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDestructive) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tintColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RenameVideoDialog(
    video: VideoModel,
    onDismissRequest: () -> Unit,
    onConfirmRename: (String) -> Unit
) {
    val nameWithoutExt = remember(video.title) {
        val file = File(video.urlOrPath)
        file.nameWithoutExtension
    }
    var newNameText by remember { mutableStateOf(nameWithoutExt) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = "Rename Video") },
        text = {
            Column {
                Text(
                    text = "Enter a new name for this video file:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = newNameText,
                    onValueChange = {
                        newNameText = it
                        errorText = null
                    },
                    label = { Text("Video Name") },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newNameText.trim().isEmpty()) {
                        errorText = "Name cannot be empty"
                    } else {
                        onConfirmRename(newNameText.trim())
                    }
                },
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("rename_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FileInfoDialog(
    video: VideoModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var metadata by remember { mutableStateOf<VideoMetadata?>(null) }

    LaunchedEffect(video.urlOrPath) {
        metadata = VideoMetadataExtractor.extractMetadataAsync(context, video.urlOrPath)
    }

    if (metadata == null) {
        GlobalCenteredLoader(onDismissRequest = onDismissRequest)
    } else {
        val currentMetadata = metadata!!
        AlertDialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("file_info_dialog"),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "File Information",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("file_info_cross_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 580.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: General Info Card
                    InfoSectionCard(title = "General Information", icon = Icons.Default.Description) {
                        InfoRow(label = "File Name", value = currentMetadata.title)
                        InfoRow(label = "File Size", value = currentMetadata.fileSizeFormatted)
                        InfoRow(label = "Duration", value = currentMetadata.durationFormatted)
                        InfoRow(label = "Container Format", value = currentMetadata.containerFormat)
                        InfoRow(label = "Location", value = currentMetadata.filePath)
                        InfoRow(label = "Last Modified", value = currentMetadata.lastModifiedFormatted)
                    }

                    // Section 2: Video & Codec Info Card
                    InfoSectionCard(title = "Video & Codec Details", icon = Icons.Default.Movie) {
                        InfoRow(label = "Resolution", value = currentMetadata.resolution)
                        InfoRow(label = "Video Codec", value = currentMetadata.videoCodec)
                        if (currentMetadata.frameRate > 0) {
                            InfoRow(label = "Frame Rate", value = String.format("%.2f FPS", currentMetadata.frameRate))
                        }
                        if (currentMetadata.bitrateKbps > 0) {
                            InfoRow(label = "Bitrate", value = "${currentMetadata.bitrateKbps} kbps")
                        }
                    }

                    // Section 3: Audio Tracks
                    InfoSectionCard(
                        title = "Audio Tracks (${currentMetadata.audioTracks.size})",
                        icon = Icons.Default.Audiotrack
                    ) {
                        if (currentMetadata.audioTracks.isEmpty()) {
                            Text(
                                text = "No internal audio tracks detected",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            currentMetadata.audioTracks.forEach { track ->
                                val cleanTitle = track.title.trim().trim('\'', '"').trim()
                                val showTitle = cleanTitle.isNotEmpty() && !cleanTitle.startsWith("Track", ignoreCase = true) && !cleanTitle.equals("und", ignoreCase = true) && !cleanTitle.equals("unknown", ignoreCase = true)
                                TrackDetailCard(
                                    trackHeader = "Track #${track.index} • ${track.language.uppercase()} ${if (showTitle) "($cleanTitle)" else ""}".trim(),
                                    details = listOf(
                                        "Codec" to track.getFormattedCodec(),
                                        "Channels" to track.getFormattedChannels(),
                                        "Sample Rate" to if (track.sampleRate > 0) "${track.sampleRate} Hz" else "Unknown",
                                        "Bitrate" to if (track.bitrate > 0) "${track.bitrate / 1000} kbps" else null
                                    )
                                )
                            }
                        }
                    }

                    // Section 4: Subtitle Tracks
                    InfoSectionCard(
                        title = "Subtitle Tracks (${currentMetadata.subtitleTracks.size})",
                        icon = Icons.Default.Subtitles
                    ) {
                        if (currentMetadata.subtitleTracks.isEmpty()) {
                            Text(
                                text = "No embedded subtitle tracks detected",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            currentMetadata.subtitleTracks.forEach { track ->
                                val cleanTitle = track.title.trim().trim('\'', '"').trim()
                                val showTitle = cleanTitle.isNotEmpty() && !cleanTitle.startsWith("Subtitle", ignoreCase = true) && !cleanTitle.equals("und", ignoreCase = true) && !cleanTitle.equals("unknown", ignoreCase = true)
                                TrackDetailCard(
                                    trackHeader = "Track #${track.index} • ${track.language.uppercase()} ${if (showTitle) "($cleanTitle)" else ""}".trim(),
                                    details = listOf(
                                        "Format" to track.getFormattedType(),
                                        "MIME Type" to track.mimeType
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2
        )
    }
}

@Composable
private fun TrackDetailCard(
    trackHeader: String,
    details: List<Pair<String, String?>>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = trackHeader,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            details.forEach { (label, value) ->
                if (!value.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderPickerDialog(
    actionType: VideoActionType,
    video: VideoModel,
    allVideos: List<VideoModel>,
    onDismissRequest: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val actionTitle = when (actionType) {
        VideoActionType.COPY -> "Copy Video to Folder"
        VideoActionType.MOVE -> "Move Video to Folder"
        else -> "Select Target Folder"
    }

    // Determine available folders from scanned videos & standard device directories
    val availableFolders = remember(allVideos) {
        val set = mutableSetOf<String>()
        
        // Add parent paths of current scanned local videos
        allVideos.forEach { v ->
            val file = File(v.urlOrPath)
            file.parentFile?.absolutePath?.let { set.add(it) }
        }

        // Add standard directories if they exist
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        
        if (downloadDir.exists()) set.add(downloadDir.absolutePath)
        if (moviesDir.exists()) set.add(moviesDir.absolutePath)
        if (dcimDir.exists()) set.add(dcimDir.absolutePath)

        set.toList().sorted()
    }

    var selectedPath by remember { mutableStateOf(availableFolders.firstOrNull() ?: "/storage/emulated/0/Download") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = when (actionType) {
                    VideoActionType.COPY -> Icons.Default.ContentCopy
                    else -> Icons.Default.DriveFileMove
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = actionTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select target folder for '${video.title}':",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(availableFolders, key = { it }) { folderPath ->
                        val folderName = File(folderPath).name
                        val isSelected = folderPath == selectedPath

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedPath = folderPath },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folderName,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = folderPath,
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFolderSelected(selectedPath) },
                modifier = Modifier.testTag("folder_picker_confirm_button")
            ) {
                Text(
                    when (actionType) {
                        VideoActionType.COPY -> "Copy Here"
                        else -> "Move Here"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    video: VideoModel,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Delete Video?",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "Are you sure you want to permanently delete '${video.title}' from your storage? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("delete_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
