package com.nativeplayer.videoplayer.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nativeplayer.videoplayer.data.VideoModel
import java.io.File

@Composable
fun DirectoryPickerDialog(
    actionType: VideoActionType,
    video: VideoModel,
    onDismissRequest: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val defaultRoot = remember {
        val storageRoot = Environment.getExternalStorageDirectory()
        if (storageRoot != null && storageRoot.exists()) storageRoot else File("/storage/emulated/0")
    }

    // Start in current video's parent directory if available, else root
    var currentDir by remember {
        val initialFile = File(video.urlOrPath)
        val parent = initialFile.parentFile
        mutableStateOf(if (parent != null && parent.exists() && parent.canRead()) parent else defaultRoot)
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val subDirectories = remember(currentDir) {
        val files = currentDir.listFiles() ?: emptyArray()
        files.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
    }

    val sourceParentPath = remember(video.urlOrPath) {
        File(video.urlOrPath).parentFile?.canonicalPath
    }
    val isSameDirectory = remember(currentDir, sourceParentPath) {
        try {
            sourceParentPath != null && currentDir.canonicalPath == sourceParentPath
        } catch (_: Exception) {
            false
        }
    }

    val actionTitle = when (actionType) {
        VideoActionType.COPY -> "Copy Video To..."
        VideoActionType.MOVE -> "Move Video To..."
        else -> "Select Target Folder"
    }

    val actionButtonText = when (actionType) {
        VideoActionType.COPY -> "Copy Here"
        VideoActionType.MOVE -> "Move Here"
        else -> "Select Here"
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {})
                    .testTag("directory_picker_dialog"),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when (actionType) {
                                    VideoActionType.COPY -> Icons.Default.ContentCopy
                                    else -> Icons.Default.DriveFileMove
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = actionTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = video.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isSameDirectory) {
                        Text(
                            text = "File is already in this directory",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Header Path Breadcrumb & Parent Navigation Row
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hasParent = currentDir.parentFile != null && currentDir.absolutePath != "/storage/emulated/0"
                            IconButton(
                                onClick = {
                                    if (hasParent) {
                                        currentDir = currentDir.parentFile!!
                                    }
                                },
                                enabled = hasParent,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Go Up",
                                    tint = if (hasParent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = currentDir.absolutePath,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Subdirectories List View
                    if (subDirectories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No subfolders in this directory",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            items(subDirectories, key = { it.absolutePath }) { folder ->
                                val childCount = remember(folder) {
                                    folder.listFiles()?.count { !it.name.startsWith(".") } ?: 0
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { currentDir = folder },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$childCount items",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open Folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action to Create New Folder on the fly
                    OutlinedButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Folder", fontSize = 13.sp)
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onFolderSelected(currentDir.absolutePath) },
                            enabled = !isSameDirectory,
                            modifier = Modifier.testTag("directory_picker_confirm_button")
                        ) {
                            Text(actionButtonText)
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentDir = currentDir,
            onDismissRequest = { showCreateFolderDialog = false },
            onFolderCreated = { newDir ->
                showCreateFolderDialog = false
                currentDir = newDir
            }
        )
    }
}

@Composable
private fun CreateFolderDialog(
    parentDir: File,
    onDismissRequest: () -> Unit,
    onFolderCreated: (File) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("New Folder") },
        text = {
            Column {
                Text(
                    text = "Folder name in ${parentDir.name}:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorText = null
                    },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = folderName.trim()
                    if (name.isEmpty()) {
                        errorText = "Folder name cannot be empty"
                    } else {
                        val newDir = File(parentDir, name)
                        if (newDir.exists()) {
                            errorText = "Folder already exists"
                        } else {
                            val created = newDir.mkdirs()
                            if (created || newDir.exists()) {
                                onFolderCreated(newDir)
                            } else {
                                errorText = "Failed to create folder"
                            }
                        }
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
