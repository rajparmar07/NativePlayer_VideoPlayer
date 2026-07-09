package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Playlist
import com.example.data.VideoModel
import com.example.viewmodel.VideoPlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import com.example.ui.components.VideoThumbnail
import com.example.ui.components.rememberVideoResolution


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalLibraryScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val localVideos by viewModel.localVideos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val resumeEnabled by viewModel.resumeFromLastLeftEnabled.collectAsState()
    val videoProgressMap by viewModel.videoProgressMap.collectAsState()

    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    var isSearchingMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }

    // Determine the correct permission for API Levels
    val permissionType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission = permissionType)

    // Scan videos automatically when permission is granted and localVideos list is empty
    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted && localVideos.isEmpty()) {
            viewModel.scanLocalVideos(context)
        }
    }

    // Group videos by folder name extracted from absolute path
    val groupedVideos = remember(localVideos) {
        localVideos.groupBy { video ->
            val file = File(video.urlOrPath)
            file.parentFile?.name ?: "Internal Memory"
        }
    }

    // Filter local videos by search query
    val filteredVideos = remember(localVideos, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            localVideos.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.urlOrPath.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Intercept back press in search mode
    BackHandler(enabled = isSearchingMode) {
        isSearchingMode = false
        searchQuery = ""
    }

    // Intercept back press in folder details view
    BackHandler(enabled = selectedFolder != null && !isSearchingMode) {
        viewModel.setSelectedFolder(null)
    }

    var showPlaylistDialog by remember { mutableStateOf<VideoModel?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("local_library_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (isSearchingMode) {
                // SEARCH MODE SCREEN
                Surface(
                    modifier = Modifier.fillMaxWidth().zIndex(1f),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isSearchingMode = false
                                searchQuery = ""
                            },
                            modifier = Modifier.testTag("exit_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Exit Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Search Videos",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-focused Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by video name or path...") },
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(focusRequester)
                        .testTag("search_text_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Request focus once search mode opens
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Results List
                if (searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Type search query to find video files...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else if (filteredVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching videos found.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp)
                    ) {
                        items(filteredVideos, key = { it.id }) { video ->
                            val progressData = videoProgressMap[video.urlOrPath]
                            LocalVideoCard(
                                video = video,
                                searchQuery = searchQuery,
                                resumeEnabled = resumeEnabled,
                                progressMs = progressData?.first ?: 0L,
                                durationMs = progressData?.second ?: video.duration,
                                onPlay = {
                                    val idx = filteredVideos.indexOf(video)
                                    viewModel.playPlaylist(filteredVideos, idx)
                                    onNavigateToPlayer()
                                },
                                onAddToPlaylist = {
                                    showPlaylistDialog = video
                                }
                            )
                        }
                    }
                }
            } else {
                // NORMAL FOLDER/VIDEO SCREEN
                // Elevated Normal Toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth().zIndex(1f),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (selectedFolder != null) {
                                IconButton(
                                    onClick = { viewModel.setSelectedFolder(null) },
                                    modifier = Modifier.testTag("back_to_folders_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back to Folders",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = selectedFolder ?: "Local Media",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (selectedFolder != null) {
                                        val count = groupedVideos[selectedFolder]?.size ?: 0
                                        if (count == 1) "1 video in folder" else "$count videos in folder"
                                    } else {
                                        val count = localVideos.size
                                        if (count == 1) "1 video on device" else "$count videos on device"
                                    },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Toolbar actions container
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Layout Switcher (Always visible in folder overview and video lists)
                            IconButton(
                                onClick = { viewModel.setGridView(!isGridView) },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .testTag("toggle_layout_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List View",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Settings Icon (leads to general settings screen overlay)
                            IconButton(
                                onClick = { onNavigateToSettings() },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!permissionState.status.isGranted) {
                    // Permission request UI
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
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permission Required",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Access is Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "To play your local device video files, we need media storage viewing permission safely.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { permissionState.launchPermissionRequest() },
                                modifier = Modifier.testTag("request_permission_button")
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                } else if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Scanning Device Storage...",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (localVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "No Videos found",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Videos Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No local video files was found on your internal/external device storage.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = selectedFolder,
                        transitionSpec = {
                            if (targetState != null) {
                                // Navigate forward (Folder -> Videos): Slide in from right, fade out folder list slightly to left
                                (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                            } else {
                                // Navigate backward (Videos -> Folder): Slide out to right, slide folder list back in from left
                                (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                            }
                        },
                        label = "folder_navigation_animation",
                        modifier = Modifier.weight(1f)
                    ) { folder ->
                        if (folder == null) {
                            // Folder Listing Overview
                            val folderList = groupedVideos.keys.toList().sorted()
                            
                            AnimatedContent(
                                targetState = isGridView,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(300, delayMillis = 90)) + 
                                     scaleIn(initialScale = 0.95f, animationSpec = tween(300, delayMillis = 90)))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                },
                                label = "folder_layout_animation",
                                modifier = Modifier.fillMaxSize()
                            ) { targetIsGrid ->
                                if (targetIsGrid) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 100.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(start = 10.dp, top = 15.dp, end = 10.dp, bottom = 80.dp)
                                    ) {
                                        items(folderList, key = { it }) { f ->
                                            val videoCount = groupedVideos[f]?.size ?: 0
                                            FolderGridCard(
                                                folderName = f,
                                                videoCount = videoCount,
                                                onClick = { viewModel.setSelectedFolder(f) }
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                        contentPadding = PaddingValues(start = 10.dp, top = 15.dp, end = 10.dp, bottom = 80.dp)
                                    ) {
                                        items(folderList, key = { it }) { f ->
                                            val videosInFolder = groupedVideos[f] ?: emptyList()
                                            val videoCount = videosInFolder.size
                                            val folderPath = remember(videosInFolder) {
                                                if (videosInFolder.isNotEmpty()) {
                                                    val file = File(videosInFolder.first().urlOrPath)
                                                    file.parentFile?.parentFile?.absolutePath ?: "/storage/emulated/0"
                                                } else {
                                                    "/storage/emulated/0"
                                                }
                                            }
                                            val totalDurationMs = remember(videosInFolder) {
                                                videosInFolder.sumOf { it.duration }
                                            }
                                            val totalDurationText = remember(totalDurationMs) {
                                                formatTotalDuration(totalDurationMs)
                                            }
                                            FolderCard(
                                                folderName = f,
                                                folderPath = folderPath,
                                                videoCount = videoCount,
                                                totalDurationText = totalDurationText,
                                                onClick = { viewModel.setSelectedFolder(f) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Nested Video List inside selected folder
                            val videosInFolder = groupedVideos[folder] ?: emptyList()
                            
                            AnimatedContent(
                                targetState = isGridView,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(300, delayMillis = 90)) + 
                                     scaleIn(initialScale = 0.95f, animationSpec = tween(300, delayMillis = 90)))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                },
                                label = "videos_layout_animation",
                                modifier = Modifier.fillMaxSize()
                            ) { targetIsGrid ->
                                if (targetIsGrid) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 150.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp)
                                    ) {
                                        items(videosInFolder, key = { it.id }) { video ->
                                            val progressData = videoProgressMap[video.urlOrPath]
                                            LocalVideoGridCard(
                                                video = video,
                                                resumeEnabled = resumeEnabled,
                                                progressMs = progressData?.first ?: 0L,
                                                durationMs = progressData?.second ?: video.duration,
                                                onPlay = {
                                                    val idx = videosInFolder.indexOf(video)
                                                    viewModel.playPlaylist(videosInFolder, idx)
                                                    onNavigateToPlayer()
                                                },
                                                onAddToPlaylist = {
                                                    showPlaylistDialog = video
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp)
                                    ) {
                                        items(videosInFolder, key = { it.id }) { video ->
                                            val progressData = videoProgressMap[video.urlOrPath]
                                            LocalVideoCard(
                                                video = video,
                                                resumeEnabled = resumeEnabled,
                                                progressMs = progressData?.first ?: 0L,
                                                durationMs = progressData?.second ?: video.duration,
                                                onPlay = {
                                                    val idx = videosInFolder.indexOf(video)
                                                    viewModel.playPlaylist(videosInFolder, idx)
                                                    onNavigateToPlayer()
                                                },
                                                onAddToPlaylist = {
                                                    showPlaylistDialog = video
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for Search (replaces refresh button completely)
        if (permissionState.status.isGranted && !isScanning && !isSearchingMode) {
            FloatingActionButton(
                onClick = {
                    isSearchingMode = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("search_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Videos"
                )
            }
        }
    }

    // Add to Playlist picker Dialog
    if (showPlaylistDialog != null) {
        val selectedVideo = showPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = null },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "No playlists found. Create one first in the Playlists section!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Choose which playlist to add '${selectedVideo.title}' to:",
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.addVideoToPlaylist(
                                                playlistId = playlist.id,
                                                title = selectedVideo.title,
                                                urlOrPath = selectedVideo.urlOrPath
                                            )
                                            showPlaylistDialog = null
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistAdd,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = playlist.title, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun FolderCard(
    folderName: String,
    folderPath: String,
    videoCount: Int,
    totalDurationText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("folder_card_$folderName"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_folder_video),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(100.dp, 70.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = folderName,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    lineBreak = LineBreak.Paragraph
                )
            )
            Text(
                text = folderPath,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    lineBreak = LineBreak.Paragraph
                )
            )
            val videoText = if (videoCount == 1) "1 Video" else "$videoCount Videos"
            Text(
                text = "$videoText • $totalDurationText",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineBreak = LineBreak.Paragraph
                )
            )
        }
    }
}

@Composable
fun FolderGridCard(
    folderName: String,
    videoCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_folder_video),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(80.dp, 55.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = folderName,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                lineBreak = LineBreak.Paragraph
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        val videoText = if (videoCount == 1) "1 Video" else "$videoCount Videos"
        Text(
            text = videoText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                lineBreak = LineBreak.Paragraph
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalVideoCard(
    video: VideoModel,
    searchQuery: String = "",
    resumeEnabled: Boolean = false,
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val file = remember(video.urlOrPath) { File(video.urlOrPath) }
    val extension = remember(file) { file.extension.uppercase().ifEmpty { "VID" } }
    val displayPath = remember(file) {
        file.parentFile?.parentFile?.absolutePath ?: "/storage/emulated/0"
    }

    val cleanTitle = remember(video.title) { video.title.substringBeforeLast('.') }
    val highlightColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("local_video_card_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media thumbnail
            Box(
                modifier = Modifier
                    .size(120.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                VideoThumbnail(
                    videoPath = video.urlOrPath,
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

                val resolution = rememberVideoResolution(video)
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

                if (resumeEnabled && progressMs > 0L) {
                    val progressRatio = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    if (progressRatio > 0f) {
                        LinearProgressIndicator(
                            progress = progressRatio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .testTag("video_progress_indicator_${video.id}"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiddleEllipsisText(
                    text = cleanTitle,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        lineBreak = LineBreak.Paragraph
                    ),
                    maxLines = 1,
                    searchQuery = searchQuery,
                    highlightColor = highlightColor
                )
                
                MiddleEllipsisText(
                    text = displayPath,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 16.sp,
                        lineBreak = LineBreak.Paragraph
                    ),
                    maxLines = 2
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Format box (extension)
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
                        text = formatDuration(video.duration),
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
                        text = formatSize(video.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
fun LocalVideoGridCard(
    video: VideoModel,
    resumeEnabled: Boolean = false,
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val file = remember(video.urlOrPath) { File(video.urlOrPath) }
    val extension = remember(file) { file.extension.uppercase().ifEmpty { "VID" } }
    val displayPath = remember(file) {
        file.parentFile?.parentFile?.absolutePath ?: "/storage/emulated/0"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("local_video_grid_card_${video.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media thumbnail (stunning 16:9 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                VideoThumbnail(
                    videoPath = video.urlOrPath,
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
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                )
                
                // Overlay duration on bottom right of the thumbnail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Overlay extension & resolution on top left of the thumbnail
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Video type (extension) badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = extension,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            lineHeight = 7.sp
                        )
                    }

                    // Resolution badge
                    val resolution = rememberVideoResolution(video)
                    if (!resolution.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
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

                if (resumeEnabled && progressMs > 0L) {
                    val progressRatio = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    if (progressRatio > 0f) {
                        LinearProgressIndicator(
                            progress = progressRatio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .testTag("video_progress_indicator_${video.id}"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
                val cleanTitle = remember(video.title) { video.title.substringBeforeLast('.') }
                MiddleEllipsisText(
                    text = cleanTitle,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    ),
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                MiddleEllipsisText(
                    text = displayPath,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = 14.sp,
                    ),
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = formatSize(video.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 14.sp,
                    )
                )
            }
        }
    }
}



fun highlightSearchText(text: String, query: String, highlightColor: Color): AnnotatedString {
    return buildAnnotatedString {
        if (query.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        while (true) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index == -1) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + query.length))
            }
            start = index + query.length
        }
    }
}

@Composable
fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
    searchQuery: String = "",
    highlightColor: Color = Color.Unspecified
) {
    val mergedStyle = LocalTextStyle.current.merge(style).copy(
        lineBreak = LineBreak.Paragraph
    )

    val processedText = remember(text, maxLines) {
        // Efficient string middle-truncation heuristic (avoids expensive measurements)
        if (maxLines == 1 && text.length > 35) {
            text.take(16) + "..." + text.takeLast(16)
        } else {
            text
        }
    }

    val annotatedText = remember(processedText, searchQuery, highlightColor) {
        if (searchQuery.isNotEmpty() && highlightColor != Color.Unspecified) {
            highlightSearchText(processedText, searchQuery, highlightColor)
        } else {
            AnnotatedString(processedText)
        }
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        style = mergedStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatTotalDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
