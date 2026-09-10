package com.nativeplayer.videoplayer.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nativeplayer.videoplayer.data.Playlist
import com.nativeplayer.videoplayer.data.PlaylistItem
import com.nativeplayer.videoplayer.data.VideoModel
import com.nativeplayer.videoplayer.ui.components.VideoThumbnail
import com.nativeplayer.videoplayer.viewmodel.PlaylistThumbnailPattern
import com.nativeplayer.videoplayer.viewmodel.VideoPlayerViewModel
import java.io.File

@Composable
fun PlaylistScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onCreatePlaylistRequested: (() -> Unit) -> Unit = {}
) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val thumbnailPattern by viewModel.playlistThumbnailPattern.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onCreatePlaylistRequested { showCreateDialog = true }
    }

    BackHandler(enabled = selectedPlaylist != null) {
        viewModel.setSelectedPlaylist(null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("playlist_screen_root")
    ) {
        if (selectedPlaylist == null) {
            // Main Playlists Overview Screen
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Playlists",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a custom playlist to group streaming links, live streams, or offline downloadable media.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp + navBarBottom)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                viewModel = viewModel,
                                thumbnailPattern = thumbnailPattern,
                                onClick = { viewModel.setSelectedPlaylist(playlist) },
                                onDelete = { viewModel.deletePlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        } else {
            // Selected Playlist Detailed Items View (Header is uniformly rendered by MainActivity's StreamCacheTopBar)
            PlaylistDetailsView(
                playlist = selectedPlaylist!!,
                viewModel = viewModel,
                onNavigateToPlayer = onNavigateToPlayer
            )
        }
    }

    // Creating Playlist Dialog
    if (showCreateDialog) {
        var playlistTitle by remember { mutableStateOf("") }
        var playlistDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_title_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = playlistDesc,
                        onValueChange = { playlistDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistTitle.isNotBlank()) {
                            viewModel.createPlaylist(playlistTitle, playlistDesc)
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_playlist_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    viewModel: VideoPlayerViewModel,
    thumbnailPattern: PlaylistThumbnailPattern,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val itemsFlow = remember(playlist.id) { viewModel.getPlaylistItems(playlist.id) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    var showMenu by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = saveCustomPlaylistCover(context, playlist.id, uri)
            if (savedPath != null) {
                viewModel.updatePlaylistCustomThumbnail(playlist.id, savedPath)
                viewModel.showSnackbar("Playlist cover updated")
            } else {
                viewModel.showSnackbar("Failed to load image")
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("playlist_card_${playlist.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail container
            PlaylistCoverThumbnail(
                playlist = playlist,
                items = items,
                pattern = thumbnailPattern,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.isSystem) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Default",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = if (items.size == 1) "1 video" else "${items.size} videos",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change Cover Image") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Image, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            coverPickerLauncher.launch("image/*")
                        }
                    )

                    if (!playlist.customThumbnailPath.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Reset Cover to Video") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.RestartAlt, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                viewModel.updatePlaylistCustomThumbnail(playlist.id, null)
                                viewModel.showSnackbar("Cover reset to default video frame")
                            }
                        )
                    }

                    if (!playlist.isSystem) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCoverThumbnail(
    playlist: Playlist,
    items: List<PlaylistItem>,
    pattern: PlaylistThumbnailPattern,
    modifier: Modifier = Modifier
) {
    val customPath = playlist.customThumbnailPath
    val customFile = remember(customPath) {
        if (!customPath.isNullOrBlank()) File(customPath) else null
    }

    if (customFile != null && customFile.exists()) {
        AsyncImage(
            model = customFile,
            contentDescription = "Playlist Cover",
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else if (items.isNotEmpty()) {
        val targetPath = if (pattern == PlaylistThumbnailPattern.FIRST_VIDEO) {
            items.first().urlOrPath
        } else {
            items.last().urlOrPath
        }

        VideoThumbnail(
            videoPath = targetPath,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            placeholder = {
                DefaultPlaylistIconPlaceholder(
                    isSystem = playlist.isSystem,
                    modifier = modifier
                )
            }
        )
    } else {
        DefaultPlaylistIconPlaceholder(
            isSystem = playlist.isSystem,
            modifier = modifier
        )
    }
}

@Composable
fun DefaultPlaylistIconPlaceholder(
    isSystem: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSystem) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSystem) Icons.Default.Bookmark else Icons.Default.PlaylistPlay,
            contentDescription = null,
            tint = if (isSystem) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun PlaylistDetailsView(
    playlist: Playlist,
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val itemsFlow = remember(playlist.id) { viewModel.getPlaylistItems(playlist.id) }
    val items by itemsFlow.collectAsState(initial = emptyList())
    var showAddItemDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Playlist Sequential Play & Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (items.isNotEmpty()) {
                        val mapped = items.map {
                            VideoModel(
                                id = "playlist_item_${it.id}",
                                title = it.title,
                                urlOrPath = it.urlOrPath,
                                subtitleUrlOrPath = it.subtitleUrl,
                                duration = it.duration,
                                isStream = it.urlOrPath.contains(".m3u8")
                            )
                        }
                        viewModel.playPlaylist(mapped, 0)
                        onNavigateToPlayer()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("play_playlist_all_button"),
                enabled = items.isNotEmpty()
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All")
            }

            OutlinedButton(
                onClick = { showAddItemDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_item_playlist_button")
            ) {
                Icon(imageVector = Icons.Default.AddLink, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Link")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryAdd,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playlist is Empty",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan files or click the 'Add Link' button above to populate it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 24.dp + navBarBottom)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val mapped = items.map {
                                    VideoModel(
                                        id = "playlist_item_${it.id}",
                                        title = it.title,
                                        urlOrPath = it.urlOrPath,
                                        subtitleUrlOrPath = it.subtitleUrl,
                                        duration = it.duration,
                                        isStream = it.urlOrPath.contains(".m3u8")
                                    )
                                }
                                val idx = items.indexOf(item)
                                viewModel.playPlaylist(mapped, idx)
                                onNavigateToPlayer()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoThumbnail(
                                videoPath = item.urlOrPath,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                placeholder = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.urlOrPath.contains(".m3u8")) Icons.Default.LiveTv else Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val displayPath = remember(item.urlOrPath) {
                                    if (item.urlOrPath.contains(".m3u8") || item.urlOrPath.startsWith("http://") || item.urlOrPath.startsWith("https://")) {
                                        "Live Stream (.m3u8)"
                                    } else {
                                        val file = File(item.urlOrPath)
                                        file.parentFile?.parentFile?.absolutePath ?: "/storage/emulated/0"
                                    }
                                }
                                Text(
                                    text = displayPath,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }

                            IconButton(onClick = { viewModel.deletePlaylistItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove Item",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Direct Link Dialog
    if (showAddItemDialog) {
        var linkTitle by remember { mutableStateOf("") }
        var linkUrl by remember { mutableStateOf("") }
        var subUrl by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Add Link to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("Server File/M3U8 Stream URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it },
                        label = { Text("Subtitle Track URL (.srt/.vtt) (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (linkTitle.isNotBlank() && linkUrl.isNotBlank()) {
                            viewModel.addVideoToPlaylist(
                                playlistId = playlist.id,
                                title = linkTitle,
                                urlOrPath = linkUrl,
                                subtitleUrl = subUrl.ifBlank { null }
                            )
                            showAddItemDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Saves a chosen image URI to internal app storage for reliable playlist thumbnail persistence.
 */
fun saveCustomPlaylistCover(context: Context, playlistId: Long, uri: android.net.Uri): String? {
    return try {
        val coversDir = File(context.filesDir, "playlist_covers")
        if (!coversDir.exists()) {
            coversDir.mkdirs()
        }

        // Clean up any existing cover files for this playlist
        coversDir.listFiles { _, name -> name.startsWith("cover_${playlistId}_") }?.forEach { it.delete() }

        val destFile = File(coversDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}
