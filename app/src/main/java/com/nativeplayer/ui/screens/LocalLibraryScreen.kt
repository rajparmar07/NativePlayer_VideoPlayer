package com.nativeplayer.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import com.nativeplayer.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.nativeplayer.data.Playlist
import com.nativeplayer.data.VideoModel
import com.nativeplayer.viewmodel.VideoPlayerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import com.nativeplayer.ui.components.VideoThumbnail
import com.nativeplayer.ui.components.rememberVideoResolution

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.nativeplayer.ui.components.VideoActionType
import com.nativeplayer.ui.components.RenameVideoDialog
import com.nativeplayer.ui.components.FileInfoDialog
import com.nativeplayer.ui.components.DirectoryPickerDialog
import com.nativeplayer.ui.components.FileOperationProgressDialog
import com.nativeplayer.ui.components.DeleteConfirmationDialog
import com.nativeplayer.ui.components.DuplicateFileDialog
import com.nativeplayer.ui.components.GlobalCenteredLoader
import com.nativeplayer.ui.components.ManageStoragePermissionDialog
import com.nativeplayer.ui.components.hasAllFilesAccessPermission
import com.nativeplayer.ui.components.BulkDeleteConfirmationDialog
import com.nativeplayer.ui.components.DisplaySettingsDialog
import com.nativeplayer.viewmodel.DisplaySettings
import com.nativeplayer.viewmodel.GridColumns
import com.nativeplayer.viewmodel.ListDisplayMode
import com.nativeplayer.viewmodel.ListStyle
import com.nativeplayer.viewmodel.SortDirection
import com.nativeplayer.viewmodel.SortField
import com.nativeplayer.viewmodel.VideoTileInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

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
    val bookmarkedVideos by viewModel.bookmarkedVideos.collectAsState()
    val resumeEnabled by viewModel.resumeFromLastLeftEnabled.collectAsState()
    val videoProgressMap by viewModel.videoProgressMap.collectAsState()
    val fileOperationState by viewModel.fileOperationState.collectAsState()

    val displaySettings by viewModel.displaySettings.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedTreePath by viewModel.selectedTreePath.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val showDisplaySettingsDialog by viewModel.showDisplaySettingsDialog.collectAsState()

    val isSearchingMode by viewModel.isSearchingMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

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

    // Memory Tree Base Path & Directory Node Calculation
    val rootTreePath = remember(localVideos) {
        if (localVideos.isEmpty()) "/storage/emulated/0"
        else {
            val paths = localVideos.map { File(it.urlOrPath).parentFile?.absolutePath ?: "" }.filter { it.isNotEmpty() }
            if (paths.isEmpty()) "/storage/emulated/0"
            else {
                var common = paths.first()
                for (p in paths) {
                    while (!p.startsWith(common) && common.isNotEmpty()) {
                        common = File(common).parentFile?.absolutePath ?: ""
                    }
                }
                var rootCandidate = common.ifEmpty { "/storage/emulated/0" }
                // Collapse redundant single-child intermediate directories (e.g. /storage/emulated/0) down to first branching folder or video parent
                while (rootCandidate.isNotEmpty()) {
                    val hasDirectVids = localVideos.any { File(it.urlOrPath).parentFile?.absolutePath == rootCandidate }
                    if (hasDirectVids) break
                    val childSubdirs = localVideos.mapNotNull { video ->
                        val p = File(video.urlOrPath).parentFile ?: return@mapNotNull null
                        var curr: File? = p
                        var child: File? = null
                        while (curr != null) {
                            if (curr.absolutePath == rootCandidate && child != null) return@mapNotNull child.absolutePath
                            child = curr
                            curr = curr.parentFile
                        }
                        null
                    }.distinct()
                    if (childSubdirs.size == 1) {
                        rootCandidate = childSubdirs.first()
                    } else {
                        break
                    }
                }
                rootCandidate
            }
        }
    }

    val currentEffectiveTreePath = selectedTreePath ?: rootTreePath

    val canGoBackTree = remember(selectedTreePath, rootTreePath) {
        selectedTreePath != null && selectedTreePath != rootTreePath && selectedTreePath!!.startsWith(rootTreePath)
    }

    val memoryTreeSubfolders = remember(localVideos, currentEffectiveTreePath) {
        val currentDir = File(currentEffectiveTreePath)
        val directSubdirs = mutableMapOf<String, MutableList<VideoModel>>()
        
        localVideos.forEach { video ->
            val vFile = File(video.urlOrPath)
            val vParent = vFile.parentFile ?: return@forEach
            var curr: File? = vParent
            var childUnderCurrent: File? = null
            while (curr != null) {
                if (curr?.absolutePath == currentDir.absolutePath && childUnderCurrent != null) {
                    val list = directSubdirs.getOrPut(childUnderCurrent.absolutePath) { mutableListOf() }
                    list.add(video)
                    break
                }
                childUnderCurrent = curr
                curr = curr?.parentFile
            }
        }
        
        directSubdirs.map { (dirPath, vList) ->
            FolderItem(
                name = File(dirPath).name,
                path = dirPath,
                videoCount = vList.size,
                totalDurationMs = vList.sumOf { it.duration },
                totalSize = vList.sumOf { it.size },
                maxDate = vList.maxOfOrNull { File(it.urlOrPath).lastModified() } ?: 0L,
                sampleVideos = vList
            )
        }
    }

    val memoryTreeDirectVideos = remember(localVideos, currentEffectiveTreePath) {
        localVideos.filter { video ->
            val parent = File(video.urlOrPath).parentFile
            parent?.absolutePath == currentEffectiveTreePath
        }
    }

    val folderItems = remember(groupedVideos) {
        groupedVideos.map { (folderName, videosInFolder) ->
            val sampleFile = File(videosInFolder.firstOrNull()?.urlOrPath ?: "")
            val folderPath = sampleFile.parentFile?.absolutePath ?: "/storage/emulated/0"
            FolderItem(
                name = folderName,
                path = folderPath,
                videoCount = videosInFolder.size,
                totalDurationMs = videosInFolder.sumOf { it.duration },
                totalSize = videosInFolder.sumOf { it.size },
                maxDate = videosInFolder.maxOfOrNull { File(it.urlOrPath).lastModified() } ?: 0L,
                sampleVideos = videosInFolder
            )
        }
    }

    // Sorted Lists
    val sortedFolderItems = remember(folderItems, displaySettings.sortField, displaySettings.sortDirection) {
        sortFolders(folderItems, displaySettings.sortField, displaySettings.sortDirection)
    }

    val sortedMemoryTreeSubfolders = remember(memoryTreeSubfolders, displaySettings.sortField, displaySettings.sortDirection) {
        sortFolders(memoryTreeSubfolders, displaySettings.sortField, displaySettings.sortDirection)
    }

    val sortedMemoryTreeVideos = remember(memoryTreeDirectVideos, displaySettings.sortField, displaySettings.sortDirection) {
        sortVideos(memoryTreeDirectVideos, displaySettings.sortField, displaySettings.sortDirection)
    }

    val sortedVideosInFolder = remember(groupedVideos, selectedFolder, displaySettings.sortField, displaySettings.sortDirection) {
        val list = groupedVideos[selectedFolder] ?: emptyList()
        sortVideos(list, displaySettings.sortField, displaySettings.sortDirection)
    }

    // Filter local videos by search query
    val filteredVideos = remember(localVideos, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val list = localVideos.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.urlOrPath.contains(searchQuery, ignoreCase = true)
            }
            sortVideos(list, displaySettings.sortField, displaySettings.sortDirection)
        }
    }

    // Intercept back press in search mode
    BackHandler(enabled = isSearchingMode) {
        viewModel.setIsSearchingMode(false)
    }

    // Intercept back press in folder details view (Folders mode)
    BackHandler(enabled = selectedFolder != null && displaySettings.displayMode == ListDisplayMode.FOLDERS && !isSearchingMode) {
        viewModel.setSelectedFolder(null)
    }

    // Safety check: if selectedFolder has no videos, reset it to null
    LaunchedEffect(localVideos, selectedFolder) {
        if (selectedFolder != null && (groupedVideos[selectedFolder] == null || groupedVideos[selectedFolder]!!.isEmpty())) {
            viewModel.setSelectedFolder(null)
        }
    }

    // Safety check: if selectedTreePath is non-null but has no subfolders/videos, reset it to null
    LaunchedEffect(localVideos, selectedTreePath) {
        if (selectedTreePath != null && memoryTreeSubfolders.isEmpty() && memoryTreeDirectVideos.isEmpty()) {
            viewModel.setSelectedTreePath(null)
        }
    }

    // Intercept back press in memory tree view (Memory Tree mode)
    BackHandler(enabled = canGoBackTree && displaySettings.displayMode == ListDisplayMode.MEMORY_TREE && !isSearchingMode) {
        val targetParent = resolveParentBranchingPath(selectedTreePath!!, rootTreePath, localVideos)
        if (targetParent != null && targetParent != selectedTreePath && targetParent != rootTreePath) {
            viewModel.setSelectedTreePath(targetParent)
        } else {
            viewModel.setSelectedTreePath(null)
        }
    }

    var showPlaylistDialogVideos by remember { mutableStateOf<List<VideoModel>?>(null) }
    var activeActionVideo by remember { mutableStateOf<VideoModel?>(null) }
    var activeActionType by remember { mutableStateOf<VideoActionType?>(null) }
    var pendingDuplicateAction by remember { mutableStateOf<Triple<VideoModel, String, VideoActionType>?>(null) }
    var showManagePermissionDialog by remember { mutableStateOf(false) }
    val selectedVideoIds by viewModel.selectedVideoIds.collectAsState()
    val showBulkDeleteDialog by viewModel.showBulkDeleteDialog.collectAsState()
    val showBulkCopyDialog by viewModel.showBulkCopyDialog.collectAsState()
    val showBulkMoveDialog by viewModel.showBulkMoveDialog.collectAsState()

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = if (selectedVideoIds.isNotEmpty()) {
        110.dp + navBarBottomPadding
    } else if (displaySettings.displayMode == ListDisplayMode.FOLDERS && selectedFolder == null) {
        104.dp + navBarBottomPadding
    } else if (displaySettings.displayMode == ListDisplayMode.MEMORY_TREE && selectedTreePath == null) {
        104.dp + navBarBottomPadding
    } else {
        90.dp + navBarBottomPadding
    }

    BackHandler(enabled = selectedVideoIds.isNotEmpty()) {
        viewModel.clearSelectedVideoIds()
    }

    val dismissActionState = remember {
        {
            activeActionVideo = null
            activeActionType = null
        }
    }

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
                Spacer(modifier = Modifier.height(12.dp))

                // Auto-focused Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by video name or path...") },
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
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
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = listBottomPadding)
                    ) {
                        items(filteredVideos, key = { it.id }) { video ->
                            val progressData = videoProgressMap[video.urlOrPath]
                            val isSelected = selectedVideoIds.contains(video.id)
                            LocalVideoCard(
                                video = video,
                                searchQuery = searchQuery,
                                resumeEnabled = resumeEnabled,
                                progressMs = progressData?.first ?: 0L,
                                durationMs = progressData?.second ?: video.duration,
                                isMultiSelectMode = selectedVideoIds.isNotEmpty(),
                                 onToggleSelect = {
                                    viewModel.toggleSelectVideoId(video.id)
                                },
                                onPlay = {
                                    val idx = filteredVideos.indexOf(video)
                                    viewModel.playPlaylist(filteredVideos, idx)
                                    onNavigateToPlayer()
                                },
                                onAddToPlaylist = {
                                    showPlaylistDialogVideos = listOf(video)
                                },
                                onLongClick = remember(video.id) {
                                    {
                                        viewModel.toggleSelectVideoId(video.id)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
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
                } else if (isScanning && localVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
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
                    PullToRefreshBox(
                        isRefreshing = isScanning,
                        onRefresh = { viewModel.scanLocalVideos(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val isGridMode = displaySettings.listStyle == ListStyle.GRID
                        val gridColsCount = displaySettings.gridColumns.count

                        if (displaySettings.displayMode == ListDisplayMode.MEMORY_TREE) {
                            // ── MEMORY TREE MODE RENDERING ─────────────────────────────────
                            AnimatedContent(
                                targetState = selectedTreePath,
                                transitionSpec = {
                                    val isNavigatingDeeper = targetState != null && (initialState == null || targetState!!.length > initialState!!.length)
                                    if (isNavigatingDeeper) {
                                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                                    } else {
                                        (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                                    }
                                },
                                label = "tree_navigation_slide_animation",
                                modifier = Modifier.fillMaxSize()
                            ) { _ ->
                                AnimatedContent(
                                    targetState = displaySettings,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(350, easing = LinearOutSlowInEasing)) + 
                                         scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + 
                                                scaleOut(targetScale = 0.96f, animationSpec = tween(200))
                                            )
                                    },
                                    label = "memory_tree_layout_animation",
                                    modifier = Modifier.fillMaxSize()
                                ) { targetSettings ->
                                    val targetIsGrid = targetSettings.listStyle == ListStyle.GRID
                                    val targetColsCount = targetSettings.gridColumns.count
                                    if (targetIsGrid) {
                                        val isDense = targetColsCount >= 4
                                        val hSpacing = if (isDense) 6.dp else if (targetColsCount == 3) 8.dp else 12.dp
                                        val vSpacing = if (isDense) 8.dp else 12.dp
                                        val contentPadH = if (isDense) 8.dp else 10.dp
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(targetColsCount),
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(vSpacing),
                                            horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                            contentPadding = PaddingValues(start = contentPadH, top = 12.dp, end = contentPadH, bottom = listBottomPadding)
                                        ) {
                                            // Render subfolders first
                                            items(sortedMemoryTreeSubfolders, key = { "folder_${it.path}" }) { f ->
                                                FolderGridCard(
                                                    folderName = f.name,
                                                    videoCount = f.videoCount,
                                                    columnsCount = targetColsCount,
                                                    onClick = { viewModel.setSelectedTreePath(resolveDeepestSingleChildPath(f.path, localVideos)) }
                                                )
                                            }

                                            // Render direct video files
                                            items(sortedMemoryTreeVideos, key = { "vid_${it.id}" }) { video ->
                                                val progressData = videoProgressMap[video.urlOrPath]
                                                val isSelected = selectedVideoIds.contains(video.id)
                                                LocalVideoGridCard(
                                                    video = video,
                                                    resumeEnabled = resumeEnabled,
                                                    progressMs = progressData?.first ?: 0L,
                                                    durationMs = progressData?.second ?: video.duration,
                                                    tileInfo = displaySettings.videoTileInfo,
                                                    columnsCount = targetColsCount,
                                                    isMultiSelectMode = selectedVideoIds.isNotEmpty(),
                                                    isSelected = isSelected,
                                                    onToggleSelect = {
                                                        viewModel.toggleSelectVideoId(video.id)
                                                    },
                                                    onPlay = {
                                                        val idx = sortedMemoryTreeVideos.indexOf(video)
                                                        viewModel.playPlaylist(sortedMemoryTreeVideos, idx)
                                                        onNavigateToPlayer()
                                                    },
                                                    onAddToPlaylist = {
                                                        showPlaylistDialogVideos = listOf(video)
                                                    },
                                                    onLongClick = remember(video.id) {
                                                        {
                                                            viewModel.toggleSelectVideoId(video.id)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            contentPadding = PaddingValues(start = 10.dp, top = 15.dp, end = 10.dp, bottom = listBottomPadding)
                                        ) {
                                            // Render subfolders first
                                            items(sortedMemoryTreeSubfolders, key = { "folder_${it.path}" }) { f ->
                                                FolderCard(
                                                    folderName = f.name,
                                                    folderPath = f.path,
                                                    videoCount = f.videoCount,
                                                    totalDurationText = formatTotalDuration(f.totalDurationMs),
                                                    onClick = { viewModel.setSelectedTreePath(f.path) }
                                                )
                                            }

                                            // Render direct video files
                                            items(sortedMemoryTreeVideos, key = { "vid_${it.id}" }) { video ->
                                                val progressData = videoProgressMap[video.urlOrPath]
                                                val isSelected = selectedVideoIds.contains(video.id)
                                                LocalVideoCard(
                                                    video = video,
                                                    resumeEnabled = resumeEnabled,
                                                    progressMs = progressData?.first ?: 0L,
                                                    durationMs = progressData?.second ?: video.duration,
                                                    tileInfo = displaySettings.videoTileInfo,
                                                    isMultiSelectMode = selectedVideoIds.isNotEmpty(),
                                                    isSelected = isSelected,
                                                    onToggleSelect = {
                                                        viewModel.toggleSelectVideoId(video.id)
                                                    },
                                                    onPlay = {
                                                        val idx = sortedMemoryTreeVideos.indexOf(video)
                                                        viewModel.playPlaylist(sortedMemoryTreeVideos, idx)
                                                        onNavigateToPlayer()
                                                    },
                                                    onAddToPlaylist = {
                                                        showPlaylistDialogVideos = listOf(video)
                                                    },
                                                    onLongClick = remember(video.id) {
                                                        {
                                                            viewModel.toggleSelectVideoId(video.id)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── FOLDERS MODE RENDERING ──────────────────────────────────────
                            AnimatedContent(
                                targetState = selectedFolder,
                                transitionSpec = {
                                    if (targetState != null) {
                                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                                    } else {
                                        (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                                    }
                                },
                                label = "folder_navigation_animation",
                                modifier = Modifier.fillMaxSize()
                            ) { folder ->
                                if (folder == null) {
                                    // Folder Listing Overview
                                    AnimatedContent(
                                        targetState = displaySettings,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(350, easing = LinearOutSlowInEasing)) + 
                                             scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                                .togetherWith(
                                                    fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + 
                                                    scaleOut(targetScale = 0.96f, animationSpec = tween(200))
                                                )
                                        },
                                        label = "folder_layout_animation",
                                        modifier = Modifier.fillMaxSize()
                                    ) { targetSettings ->
                                        val targetIsGrid = targetSettings.listStyle == ListStyle.GRID
                                        val targetColsCount = targetSettings.gridColumns.count
                                        if (targetIsGrid) {
                                            val isDense = targetColsCount >= 4
                                            val hSpacing = if (isDense) 6.dp else if (targetColsCount == 3) 8.dp else 12.dp
                                            val vSpacing = if (isDense) 8.dp else 12.dp
                                            val contentPadH = if (isDense) 8.dp else 10.dp
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(targetColsCount),
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(vSpacing),
                                                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                                contentPadding = PaddingValues(start = contentPadH, top = 12.dp, end = contentPadH, bottom = listBottomPadding)
                                            ) {
                                                items(sortedFolderItems, key = { it.name }) { f ->
                                                    FolderGridCard(
                                                        folderName = f.name,
                                                        videoCount = f.videoCount,
                                                        columnsCount = targetColsCount,
                                                        onClick = { viewModel.setSelectedFolder(f.name) }
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                                contentPadding = PaddingValues(start = 10.dp, top = 15.dp, end = 10.dp, bottom = listBottomPadding)
                                            ) {
                                                items(sortedFolderItems, key = { it.name }) { f ->
                                                    FolderCard(
                                                        folderName = f.name,
                                                        folderPath = f.path,
                                                        videoCount = f.videoCount,
                                                        totalDurationText = formatTotalDuration(f.totalDurationMs),
                                                        onClick = { viewModel.setSelectedFolder(f.name) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Nested Video List inside selected folder
                                    AnimatedContent(
                                        targetState = displaySettings,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(350, easing = LinearOutSlowInEasing)) + 
                                             scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                                .togetherWith(
                                                    fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) + 
                                                    scaleOut(targetScale = 0.96f, animationSpec = tween(200))
                                                )
                                        },
                                        label = "videos_layout_animation",
                                        modifier = Modifier.fillMaxSize()
                                    ) { targetSettings ->
                                        val targetIsGrid = targetSettings.listStyle == ListStyle.GRID
                                        val targetColsCount = targetSettings.gridColumns.count
                                        if (targetIsGrid) {
                                            val isDense = targetColsCount >= 4
                                            val hSpacing = if (isDense) 6.dp else if (targetColsCount == 3) 8.dp else 12.dp
                                            val vSpacing = if (isDense) 8.dp else 12.dp
                                            val contentPadH = if (isDense) 8.dp else 12.dp
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(targetColsCount),
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(vSpacing),
                                                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                                                contentPadding = PaddingValues(start = contentPadH, top = 12.dp, end = contentPadH, bottom = listBottomPadding)
                                            ) {
                                                items(sortedVideosInFolder, key = { it.id }) { video ->
                                                    val progressData = videoProgressMap[video.urlOrPath]
                                                    val isSelected = selectedVideoIds.contains(video.id)
                                                    LocalVideoGridCard(
                                                        video = video,
                                                        resumeEnabled = resumeEnabled,
                                                        progressMs = progressData?.first ?: 0L,
                                                        durationMs = progressData?.second ?: video.duration,
                                                        tileInfo = displaySettings.videoTileInfo,
                                                        columnsCount = targetColsCount,
                                                        isMultiSelectMode = selectedVideoIds.isNotEmpty(),
                                                        isSelected = isSelected,
                                                        onToggleSelect = {
                                                            viewModel.toggleSelectVideoId(video.id)
                                                        },
                                                        onPlay = {
                                                            val idx = sortedVideosInFolder.indexOf(video)
                                                            viewModel.playPlaylist(sortedVideosInFolder, idx)
                                                            onNavigateToPlayer()
                                                        },
                                                        onAddToPlaylist = {
                                                            showPlaylistDialogVideos = listOf(video)
                                                        },
                                                        onLongClick = remember(video.id) {
                                                            {
                                                                viewModel.toggleSelectVideoId(video.id)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = listBottomPadding)
                                            ) {
                                                items(sortedVideosInFolder, key = { it.id }) { video ->
                                                    val progressData = videoProgressMap[video.urlOrPath]
                                                    val isSelected = selectedVideoIds.contains(video.id)
                                                    LocalVideoCard(
                                                        video = video,
                                                        resumeEnabled = resumeEnabled,
                                                        progressMs = progressData?.first ?: 0L,
                                                        durationMs = progressData?.second ?: video.duration,
                                                        tileInfo = displaySettings.videoTileInfo,
                                                        isMultiSelectMode = selectedVideoIds.isNotEmpty(),
                                                        isSelected = isSelected,
                                                        onToggleSelect = {
                                                            viewModel.toggleSelectVideoId(video.id)
                                                        },
                                                        onPlay = {
                                                            val idx = sortedVideosInFolder.indexOf(video)
                                                            viewModel.playPlaylist(sortedVideosInFolder, idx)
                                                            onNavigateToPlayer()
                                                        },
                                                        onAddToPlaylist = {
                                                            showPlaylistDialogVideos = listOf(video)
                                                        },
                                                        onLongClick = remember(video.id) {
                                                            {
                                                                viewModel.toggleSelectVideoId(video.id)
                                                            }
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
            }
        }

        // Floating Action Button for Search (Inside root Box)
        if (permissionState.status.isGranted && !isScanning && !isSearchingMode && selectedVideoIds.isEmpty()) {
            FloatingActionButton(
                onClick = {
                    viewModel.setIsSearchingMode(true)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .size(56.dp)
                    .testTag("search_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Videos",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Docked Contextual Bottom Actions Bar for Multi/Single Selection
        AnimatedVisibility(
            visible = selectedVideoIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedVideos = remember(selectedVideoIds, localVideos) {
                localVideos.filter { selectedVideoIds.contains(it.id) }
            }
            val isBookmarked = remember(selectedVideos, bookmarkedVideos) {
                if (selectedVideos.isEmpty()) false
                else {
                    val bookmarkedUrls = bookmarkedVideos.map { it.urlOrPath }.toSet()
                    selectedVideos.all { bookmarkedUrls.contains(it.urlOrPath) }
                }
            }
            VideoSelectionBottomBar(
                selectedCount = selectedVideos.size,
                isBookmarked = isBookmarked,
                onRename = {
                    if (!hasAllFilesAccessPermission()) {
                        showManagePermissionDialog = true
                    } else if (selectedVideos.isNotEmpty()) {
                        activeActionVideo = selectedVideos.first()
                        activeActionType = VideoActionType.RENAME
                    }
                },
                onFileInfo = {
                    if (selectedVideos.isNotEmpty()) {
                        activeActionVideo = selectedVideos.first()
                        activeActionType = VideoActionType.FILE_INFO
                    }
                },
                onBookmark = {
                    if (selectedVideos.isNotEmpty()) {
                        viewModel.toggleBookmark(selectedVideos) { isNowBookmarked, affectedCount ->
                            val message = if (isNowBookmarked) {
                                if (affectedCount == 1) "Added to Bookmarks"
                                else if (affectedCount == 0) "Already bookmarked"
                                else "$affectedCount videos added to Bookmarks"
                            } else {
                                if (affectedCount == 1) "Removed from Bookmarks"
                                else "$affectedCount videos removed from Bookmarks"
                            }
                            viewModel.showSnackbar(message)
                            viewModel.clearSelectedVideoIds()
                        }
                    }
                },
                onAddToPlaylist = {
                    if (selectedVideos.isNotEmpty()) {
                        showPlaylistDialogVideos = selectedVideos
                    }
                },
                onCopy = {
                    if (selectedVideos.size == 1) {
                        activeActionVideo = selectedVideos.first()
                        activeActionType = VideoActionType.COPY
                    } else if (selectedVideos.size > 1) {
                        viewModel.setShowBulkCopyDialog(true)
                    }
                },
                onMove = {
                    if (!hasAllFilesAccessPermission()) {
                        showManagePermissionDialog = true
                    } else {
                        if (selectedVideos.size == 1) {
                            activeActionVideo = selectedVideos.first()
                            activeActionType = VideoActionType.MOVE
                        } else if (selectedVideos.size > 1) {
                            viewModel.setShowBulkMoveDialog(true)
                        }
                    }
                },
                onDelete = {
                    if (!hasAllFilesAccessPermission()) {
                        showManagePermissionDialog = true
                    } else {
                        if (selectedVideos.size == 1) {
                            activeActionVideo = selectedVideos.first()
                            activeActionType = VideoActionType.DELETE
                        } else if (selectedVideos.size > 1) {
                            viewModel.setShowBulkDeleteDialog(true)
                        }
                    }
                }
            )
        }
    }

    // Add to Playlist picker Dialog
    if (showPlaylistDialogVideos != null) {
        val selectedVideos = showPlaylistDialogVideos!!
        val userPlaylists = remember(playlists) {
            playlists.filter { !it.isSystem && it.title != "Bookmarks" }
        }
        AlertDialog(
            onDismissRequest = { showPlaylistDialogVideos = null },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    if (userPlaylists.isEmpty()) {
                        Text(
                            text = "No playlists found. Create one first in the Playlists section!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val promptText = if (selectedVideos.size == 1) {
                            "Choose which playlist to add '${selectedVideos.first().title}' to:"
                        } else {
                            "Choose which playlist to add ${selectedVideos.size} videos to:"
                        }
                        Text(
                            text = promptText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(userPlaylists, key = { it.id }) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (selectedVideos.size == 1) {
                                                viewModel.addVideoToPlaylist(
                                                    playlistId = playlist.id,
                                                    title = selectedVideos.first().title,
                                                    urlOrPath = selectedVideos.first().urlOrPath
                                                )
                                                viewModel.showSnackbar("Added to playlist")
                                                viewModel.clearSelectedVideoIds()
                                            } else {
                                                viewModel.addMultipleVideosToPlaylist(
                                                    playlistId = playlist.id,
                                                    videos = selectedVideos
                                                )
                                                viewModel.showSnackbar("${selectedVideos.size} videos added to playlist")
                                                viewModel.clearSelectedVideoIds()
                                            }
                                            showPlaylistDialogVideos = null
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
                TextButton(onClick = { showPlaylistDialogVideos = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showManagePermissionDialog) {
        ManageStoragePermissionDialog(
            onDismissRequest = { showManagePermissionDialog = false }
        )
    }

    if (activeActionVideo != null && activeActionType == VideoActionType.RENAME) {
        RenameVideoDialog(
            video = activeActionVideo!!,
            onDismissRequest = dismissActionState,
            onConfirmRename = { newName ->
                val v = activeActionVideo!!
                dismissActionState()
                viewModel.renameVideo(context, v, newName) { success, err ->
                    if (success) {
                        viewModel.showSnackbar("Renamed successfully")
                        viewModel.clearSelectedVideoIds()
                    } else {
                        viewModel.showSnackbar(err ?: "Rename failed")
                    }
                }
            }
        )
    }

    if (activeActionVideo != null && activeActionType == VideoActionType.FILE_INFO) {
        FileInfoDialog(
            video = activeActionVideo!!,
            onDismissRequest = dismissActionState
        )
    }

    if (activeActionVideo != null && (activeActionType == VideoActionType.COPY || activeActionType == VideoActionType.MOVE)) {
        DirectoryPickerDialog(
            actionType = activeActionType!!,
            video = activeActionVideo!!,
            onDismissRequest = dismissActionState,
            onFolderSelected = { targetFolder ->
                val v = activeActionVideo!!
                val currentAction = activeActionType!!
                dismissActionState()

                val srcFile = File(v.urlOrPath)
                val targetFile = File(targetFolder, srcFile.name)

                if (targetFile.exists()) {
                    pendingDuplicateAction = Triple(v, targetFolder, currentAction)
                } else {
                    when (currentAction) {
                        VideoActionType.COPY -> {
                            viewModel.copyVideoWithProgress(context, v, targetFolder, overwrite = false) { success, err ->
                                if (success) {
                                    viewModel.showSnackbar("Video copied successfully")
                                    viewModel.clearSelectedVideoIds()
                                } else {
                                    viewModel.showSnackbar(err ?: "Copy failed")
                                }
                            }
                        }
                        VideoActionType.MOVE -> {
                            val currentFolderToChecking = selectedFolder
                            val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                            viewModel.moveVideoWithProgress(context, v, targetFolder, overwrite = false) { success, err ->
                                if (success) {
                                    viewModel.showSnackbar("Video moved successfully")
                                    viewModel.clearSelectedVideoIds()
                                    if (currentFolderToChecking != null && count <= 1) {
                                        viewModel.setSelectedFolder(null)
                                    }
                                } else {
                                    viewModel.showSnackbar(err ?: "Move failed")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        )
    }

    if (pendingDuplicateAction != null) {
        val (v, targetFolder, actionType) = pendingDuplicateAction!!
        DuplicateFileDialog(
            video = v,
            targetFolderPath = targetFolder,
            actionType = actionType,
            onDismissRequest = { pendingDuplicateAction = null },
            onReplace = {
                pendingDuplicateAction = null
                when (actionType) {
                    VideoActionType.COPY -> {
                        viewModel.copyVideoWithProgress(context, v, targetFolder, overwrite = true) { success, err ->
                            if (success) {
                                viewModel.showSnackbar("Video copied and overwritten")
                                viewModel.clearSelectedVideoIds()
                            } else {
                                viewModel.showSnackbar(err ?: "Copy failed")
                            }
                        }
                    }
                    VideoActionType.MOVE -> {
                        val currentFolderToChecking = selectedFolder
                        val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                        viewModel.moveVideoWithProgress(context, v, targetFolder, overwrite = true) { success, err ->
                            if (success) {
                                viewModel.showSnackbar("Video moved and overwritten")
                                viewModel.clearSelectedVideoIds()
                                if (currentFolderToChecking != null && count <= 1) {
                                    viewModel.setSelectedFolder(null)
                                }
                            } else {
                                viewModel.showSnackbar(err ?: "Move failed")
                            }
                        }
                    }
                    else -> {}
                }
            },
            onKeepBoth = {
                pendingDuplicateAction = null
                when (actionType) {
                    VideoActionType.COPY -> {
                        viewModel.copyVideoWithProgress(context, v, targetFolder, overwrite = false) { success, err ->
                            if (success) {
                                viewModel.showSnackbar("Video copied (renamed)")
                                viewModel.clearSelectedVideoIds()
                            } else {
                                viewModel.showSnackbar(err ?: "Copy failed")
                            }
                        }
                    }
                    VideoActionType.MOVE -> {
                        val currentFolderToChecking = selectedFolder
                        val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                        viewModel.moveVideoWithProgress(context, v, targetFolder, overwrite = false) { success, err ->
                            if (success) {
                                viewModel.showSnackbar("Video moved (renamed)")
                                viewModel.clearSelectedVideoIds()
                                if (currentFolderToChecking != null && count <= 1) {
                                    viewModel.setSelectedFolder(null)
                                }
                            } else {
                                viewModel.showSnackbar(err ?: "Move failed")
                            }
                        }
                    }
                    else -> {}
                }
            }
        )
    }

    // Real-Time File Operation Progress Dialog
    if (fileOperationState != null && fileOperationState!!.isRunning) {
        FileOperationProgressDialog(state = fileOperationState!!)
    }

    if (activeActionVideo != null && activeActionType == VideoActionType.DELETE) {
        DeleteConfirmationDialog(
            video = activeActionVideo!!,
            onDismissRequest = dismissActionState,
            onConfirmDelete = {
                val v = activeActionVideo!!
                val currentFolderToChecking = selectedFolder
                val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                dismissActionState()
                viewModel.deleteVideo(context, v) { success, err ->
                    if (success) {
                        viewModel.showSnackbar("Video deleted")
                        viewModel.clearSelectedVideoIds()
                        if (currentFolderToChecking != null && count <= 1) {
                            viewModel.setSelectedFolder(null)
                        }
                    } else {
                        viewModel.showSnackbar(err ?: "Delete failed")
                    }
                }
            }
        )
    }

    if (showBulkCopyDialog && selectedVideoIds.isNotEmpty()) {
        val currentList = if (selectedFolder != null) (groupedVideos[selectedFolder] ?: emptyList()) else localVideos
        val selectedVideos = currentList.filter { selectedVideoIds.contains(it.id) }
        if (selectedVideos.isNotEmpty()) {
            DirectoryPickerDialog(
                actionType = VideoActionType.COPY,
                video = selectedVideos.first(),
                onDismissRequest = { viewModel.setShowBulkCopyDialog(false) },
                onFolderSelected = { targetFolder ->
                    viewModel.setShowBulkCopyDialog(false)
                    viewModel.copyMultipleVideosWithProgress(context, selectedVideos, targetFolder) { success, err ->
                        if (success) {
                            viewModel.showSnackbar("${selectedVideos.size} videos copied")
                            viewModel.clearSelectedVideoIds()
                        } else {
                            viewModel.showSnackbar(err ?: "Bulk copy failed")
                        }
                    }
                }
            )
        }
    }

    if (showBulkMoveDialog && selectedVideoIds.isNotEmpty()) {
        val currentList = if (selectedFolder != null) (groupedVideos[selectedFolder] ?: emptyList()) else localVideos
        val selectedVideos = currentList.filter { selectedVideoIds.contains(it.id) }
        if (selectedVideos.isNotEmpty()) {
            DirectoryPickerDialog(
                actionType = VideoActionType.MOVE,
                video = selectedVideos.first(),
                onDismissRequest = { viewModel.setShowBulkMoveDialog(false) },
                onFolderSelected = { targetFolder ->
                    viewModel.setShowBulkMoveDialog(false)
                    val currentFolderToChecking = selectedFolder
                    val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                    val moveCount = selectedVideos.size
                    viewModel.moveMultipleVideosWithProgress(context, selectedVideos, targetFolder) { success, err ->
                        if (success) {
                            viewModel.showSnackbar("$moveCount videos moved")
                            viewModel.clearSelectedVideoIds()
                            if (currentFolderToChecking != null && count <= moveCount) {
                                viewModel.setSelectedFolder(null)
                            }
                        } else {
                            viewModel.showSnackbar(err ?: "Bulk move failed")
                        }
                    }
                }
            )
        }
    }

    if (showBulkDeleteDialog && selectedVideoIds.isNotEmpty()) {
        val currentList = if (selectedFolder != null) (groupedVideos[selectedFolder] ?: emptyList()) else localVideos
        val selectedVideos = currentList.filter { selectedVideoIds.contains(it.id) }
        BulkDeleteConfirmationDialog(
            count = selectedVideos.size,
            onDismissRequest = { viewModel.setShowBulkDeleteDialog(false) },
            onConfirmDelete = {
                viewModel.setShowBulkDeleteDialog(false)
                val currentFolderToChecking = selectedFolder
                val count = groupedVideos[currentFolderToChecking]?.size ?: 0
                val deleteCount = selectedVideos.size
                viewModel.deleteMultipleVideos(context, selectedVideos) { success, err ->
                    if (success) {
                        viewModel.showSnackbar("$deleteCount videos deleted")
                        viewModel.clearSelectedVideoIds()
                        if (currentFolderToChecking != null && count <= deleteCount) {
                            viewModel.setSelectedFolder(null)
                        }
                    } else {
                        viewModel.showSnackbar(err ?: "Bulk delete failed")
                    }
                }
            }
        )
    }

    // Display & Layout Settings Dialog (Uniform Bottom Sheet)
    if (showDisplaySettingsDialog) {
        DisplaySettingsDialog(
            initialSettings = displaySettings,
            onDismissRequest = { viewModel.setShowDisplaySettingsDialog(false) },
            onApply = { updatedSettings ->
                viewModel.updateDisplaySettings(updatedSettings)
            }
        )
    }
}

private const val FOLDER_PATH_1 = "M159.95,0.5C174.95,0.59 185.99,4.32 196.78,14.6V14.6C198.52,16.38 200.1,18.26 201.71,20.2L201.71,20.21C205.93,25.13 208.1,27.66 210.71,29.48C213.31,31.31 216.35,32.42 222.27,34.59L222.33,34.62L222.41,34.62C226.98,34.9 231.51,34.83 236.07,34.7C238.11,34.7 240.15,34.7 242.19,34.72H242.19C247.71,34.73 253.22,34.65 258.73,34.55L258.73,34.55C261.62,34.51 264.51,34.49 267.4,34.48L276.08,34.47C287.02,34.46 297.96,34.36 308.9,34.24C324.97,34.05 341.03,33.99 357.1,33.93C359.47,33.92 361.85,33.91 364.22,33.9C365.38,33.9 366.54,33.89 367.73,33.89C374.61,33.86 381.48,33.81 388.36,33.73C392.63,33.69 396.91,33.66 401.18,33.62H401.18C403.16,33.61 405.14,33.58 407.12,33.55C409.82,33.52 412.52,33.49 415.23,33.48H415.23C416.01,33.46 416.79,33.45 417.59,33.43C423.45,33.42 428.81,34.37 433.33,38.2C433.76,38.76 434.19,39.32 434.63,39.9L434.64,39.91C435.09,40.48 435.54,41.06 436.01,41.65C438.31,44.96 438.08,47.38 438.07,51.6V51.6C438.07,52.1 438.07,52.34 438.06,52.59L438.06,53.61C438.06,55.72 438.05,57.84 438.03,59.96V59.96C438.03,61.39 438.02,62.83 438.02,64.26C438.01,67.78 437.99,71.3 437.97,74.82L437.97,75.29L438.44,75.32C439.14,75.37 439.83,75.42 440.55,75.47V75.47C452.9,76.42 461.54,80.48 469.99,89.65H469.99C480.79,102.31 480.11,117.45 480.04,133.39V133.39L480.06,140.28C480.08,146.49 480.08,152.69 480.06,158.9C480.06,162.78 480.06,166.66 480.06,170.55C480.06,171.16 480.06,171.87 480.06,172.51C480.07,173.82 480.07,175.13 480.07,176.45C480.08,188.74 480.07,201.03 480.05,213.32C480.03,223.85 480.03,234.37 480.05,244.9C480.07,257.15 480.08,269.4 480.07,281.65C480.06,282.96 480.06,284.27 480.06,285.58C480.06,286.58 480.06,286.51 480.06,287.53C480.06,292.05 480.06,296.56 480.07,301.08C480.08,307.16 480.07,313.25 480.05,319.34V319.34C480.05,321.57 480.05,323.8 480.06,326.02C480.11,342.2 479.62,356.29 468.05,368.52C456.35,379.6 443.75,381.01 428.41,381.05H426.92C425.24,381.06 423.57,381.06 421.89,381.06C417.3,381.07 412.71,381.07 408.12,381.07L393.26,381.08C383.56,381.09 373.86,381.1 364.15,381.09C356.27,381.09 348.38,381.09 340.49,381.1C339.37,381.1 338.25,381.1 337.09,381.1C334.81,381.1 332.53,381.1 330.25,381.1C319.56,381.11 308.86,381.11 298.17,381.11L266.09,381.1C246.53,381.1 226.97,381.11 207.41,381.13C187.32,381.15 167.23,381.16 147.14,381.15C135.86,381.15 124.59,381.15 113.31,381.17C103.71,381.18 94.11,381.18 84.51,381.17C79.61,381.16 74.72,381.16 69.82,381.17C65.33,381.18 60.85,381.18 56.36,381.17H53.93C53.12,381.17 52.31,381.17 51.5,381.17C35.8,381.24 23.45,378.8 11.82,367.88C2.69,358.15 0.69,346.81 0.64,333.85V332.59C0.63,331.32 0.63,330.06 0.62,328.76C0.62,327.02 0.61,325.28 0.61,323.54L0.62,318.32C0.62,314.56 0.61,310.8 0.6,307.03C0.58,299.67 0.58,292.31 0.59,284.95C0.59,278.96 0.59,272.98 0.59,266.99C0.59,266.14 0.59,265.28 0.58,264.4C0.58,262.67 0.58,260.93 0.58,259.2C0.57,242.96 0.57,226.71 0.58,210.46C0.59,195.62 0.58,180.77 0.56,165.93C0.53,150.66 0.52,135.4 0.53,120.14C0.53,111.57 0.53,103.01 0.52,94.45C0.5,87.16 0.5,79.88 0.52,72.59C0.53,68.88 0.53,65.16 0.51,61.45C0.5,57.41 0.51,53.38 0.53,49.34V49.33C0.52,48.18 0.51,47.02 0.5,45.83C0.61,33.33 3.87,22.29 12.5,13.16C23.56,2.69 35.32,0.59 50.21,0.62H50.21C51.49,0.61 52.76,0.61 54.08,0.6C57.55,0.59 61.02,0.59 64.5,0.59C67.41,0.59 70.32,0.59 73.23,0.58C80.11,0.57 86.98,0.57 93.86,0.58C100.93,0.58 107.99,0.57 115.06,0.55C121.15,0.53 127.24,0.53 133.33,0.53C135.14,0.53 136.96,0.53 138.77,0.53L144.21,0.52C147.63,0.5 151.04,0.51 154.45,0.52H154.46C156.29,0.52 158.12,0.51 159.95,0.5Z"
private const val FOLDER_PATH_2 = "M304.09,156.04C304.84,156.63 305.59,157.22 306.36,157.83C323.65,172.16 335.77,194.9 338.62,217.11C340.84,247.36 334.84,274.51 314.81,297.82C314.14,298.57 313.48,299.32 312.79,300.09C312.28,300.68 311.77,301.27 311.25,301.87C298.2,315.98 280.06,324.85 261.55,329.09C260.84,329.26 260.12,329.43 259.39,329.61C237.35,334.5 214.72,330.53 194.84,320.39C193.82,319.88 192.79,319.36 191.74,318.83C169.79,306.84 153.35,286.34 145.74,262.63C145.35,261.26 144.95,259.89 144.57,258.52C144.31,257.67 144.05,256.83 143.79,255.96C141.56,248.06 141.14,240.54 141.18,232.35C141.18,231.7 141.19,231.04 141.19,230.36C141.35,207.33 148.94,186.29 163.9,168.6C164.79,167.49 164.79,167.49 165.69,166.34C180.37,148.57 202.35,137.55 224.81,133.8C225.57,133.67 226.33,133.55 227.11,133.41C255.13,129.59 282.52,138.23 304.09,156.04Z"
private const val FOLDER_PATH_3 = "M216.11,33.25C243.08,33.19 270.05,33.13 297.01,33.1C309.54,33.09 322.06,33.07 334.58,33.03C345.49,33.01 356.41,32.99 367.32,32.98C373.1,32.98 378.88,32.97 384.66,32.95C390.1,32.93 395.54,32.92 400.98,32.93C402.97,32.92 404.97,32.92 406.96,32.91C409.69,32.89 412.42,32.9 415.15,32.9C415.93,32.9 416.72,32.89 417.53,32.88C423.47,32.93 429.03,33.87 433.69,37.85C434.13,38.43 434.57,39 435.03,39.6C435.48,40.18 435.94,40.76 436.41,41.35C438.8,44.78 438.6,47.77 438.57,51.81C438.56,52.84 438.56,52.84 438.56,53.88C438.56,56.07 438.54,58.26 438.53,60.44C438.53,61.93 438.52,63.41 438.52,64.89C438.51,68.52 438.49,72.16 438.47,75.79C416.45,75.86 394.43,75.91 372.42,75.94C362.19,75.96 351.97,75.98 341.74,76.01C332.83,76.04 323.92,76.06 315,76.07C310.29,76.07 305.57,76.08 300.85,76.1C296.4,76.12 291.96,76.12 287.51,76.12C285.89,76.12 284.26,76.13 282.63,76.14C260.73,76.28 249.34,68.35 234.05,53.29C232.44,51.69 230.83,50.09 229.22,48.48C227.56,46.82 225.9,45.17 224.23,43.52C223.17,42.47 222.11,41.41 221.06,40.36C220.31,39.62 220.31,39.62 219.55,38.87C216.11,35.41 216.11,35.41 216.11,33.25Z"
private const val FOLDER_PATH_4 = "M233.64,192.91C235.91,194.43 238.1,196.03 240.28,197.67C241.97,198.9 243.66,200.13 245.36,201.35C246.25,202 247.15,202.65 248.07,203.32C254.04,207.57 260.2,211.55 266.34,215.57C280.66,225.03 280.66,225.03 281.85,229.51C281.75,235.12 279.91,238.22 276.05,242.08C273.93,243.66 271.87,245.09 269.64,246.49C268.35,247.33 267.06,248.16 265.77,249C264.75,249.66 264.75,249.66 263.7,250.34C260.65,252.34 257.65,254.4 254.65,256.47C251.56,258.6 248.46,260.72 245.35,262.83C243.32,264.22 241.29,265.64 239.27,267.05C238.1,267.85 236.92,268.65 235.75,269.45C234.75,270.15 233.74,270.84 232.71,271.56C228.7,273.47 226.32,273.42 221.91,273.02C218.47,270.92 216.59,269.01 215.14,265.29C214.88,262.33 214.88,262.33 214.86,258.81C214.85,258.17 214.85,257.53 214.84,256.87C214.82,254.75 214.82,252.63 214.82,250.52C214.81,249.04 214.8,247.57 214.8,246.09C214.79,243 214.78,239.9 214.78,236.81C214.78,232.85 214.76,228.9 214.72,224.95C214.7,221.9 214.7,218.85 214.7,215.8C214.7,214.34 214.69,212.88 214.67,211.43C214.65,209.38 214.66,207.34 214.67,205.3C214.67,204.14 214.67,202.98 214.66,201.78C215.24,197.91 216.59,195.82 219.01,192.77C223.58,189.85 228.98,190.42 233.64,192.91Z"

@Composable
fun rememberThemedFolderVectorPainter(
    primaryColor: Color = MaterialTheme.colorScheme.primary
): VectorPainter {
    val path1Nodes = remember { addPathNodes(FOLDER_PATH_1) }
    val path2Nodes = remember { addPathNodes(FOLDER_PATH_2) }
    val path3Nodes = remember { addPathNodes(FOLDER_PATH_3) }
    val path4Nodes = remember { addPathNodes(FOLDER_PATH_4) }

    val mainBodyFill = primaryColor
    val circlePocketFill = Color.Black.copy(alpha = 0.18f)
    val topTabHighlightFill = Color.White.copy(alpha = 0.35f)
    val borderStrokeColor = Color.Black.copy(alpha = 0.30f)
    val playButtonFill = Color(0xFFFBFDFE)

    val imageVector = remember(primaryColor) {
        ImageVector.Builder(
            name = "ThemedFolderVideoVector",
            defaultWidth = 481.dp,
            defaultHeight = 382.dp,
            viewportWidth = 481f,
            viewportHeight = 382f
        ).apply {
            addPath(
                pathData = path1Nodes,
                fill = SolidColor(mainBodyFill),
                stroke = SolidColor(borderStrokeColor),
                strokeLineWidth = 6f
            )
            addPath(
                pathData = path2Nodes,
                fill = SolidColor(circlePocketFill)
            )
            addPath(
                pathData = path3Nodes,
                fill = SolidColor(topTabHighlightFill)
            )
            addPath(
                pathData = path4Nodes,
                fill = SolidColor(playButtonFill)
            )
        }.build()
    }
    return rememberVectorPainter(imageVector)
}

@Composable
fun ThemedFolderIcon(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val folderPainter = rememberThemedFolderVectorPainter(primaryColor = primaryColor)
    Image(
        painter = folderPainter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
fun FolderCard(
    folderName: String,
    folderPath: String,
    videoCount: Int,
    totalDurationText: String,
    onClick: () -> Unit
) {
    val displayName = remember(folderName) {
        if (folderName == "0" || folderName.isEmpty() || folderName == "emulated") "Internal Storage" else folderName
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("folder_card_$folderName"),
        verticalAlignment = Alignment.Top
    ) {
        ThemedFolderIcon(
            modifier = Modifier
                .align(Alignment.Top)
                .size(90.dp, 64.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayName,
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
                    lineHeight = 16.sp,
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
    columnsCount: Int = 3,
    onClick: () -> Unit
) {
    val displayName = remember(folderName) {
        if (folderName == "0" || folderName.isEmpty() || folderName == "emulated") "Internal Storage" else folderName
    }
    val isDense = columnsCount >= 4
    val cardRadius = if (isDense) 8.dp else 12.dp
    val textPaddingH = if (isDense) 4.dp else if (columnsCount == 3) 6.dp else 8.dp
    val textPaddingV = if (isDense) 4.dp else if (columnsCount == 3) 5.dp else 6.dp
    val titleFontSize = if (isDense) 11.5.sp else if (columnsCount == 3) 13.sp else 14.sp
    val titleLineHeight = if (isDense) 14.sp else if (columnsCount == 3) 16.sp else 18.sp
    val subFontSize = if (isDense) 9.5.sp else if (columnsCount == 3) 11.sp else 12.sp
    val subLineHeight = if (isDense) 12.sp else if (columnsCount == 3) 14.sp else 16.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("folder_grid_card_$folderName"),
        shape = RoundedCornerShape(cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Folder Icon container utilizing available horizontal space cleanly without background fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius))
                    .padding(horizontal = if (isDense) 2.dp else 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                ThemedFolderIcon(
                    modifier = Modifier
                        .fillMaxSize(if (isDense) 0.96f else 0.92f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = textPaddingH, vertical = textPaddingV),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isDense) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        lineBreak = LineBreak.Paragraph
                    )
                )
                Spacer(modifier = Modifier.height(if (isDense) 1.dp else 2.dp))
                val videoText = if (videoCount == 1) "1 Video" else "$videoCount Videos"
                Text(
                    text = videoText,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = subFontSize,
                        lineHeight = subLineHeight,
                        lineBreak = LineBreak.Paragraph
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LocalVideoCard(
    video: VideoModel,
    searchQuery: String = "",
    resumeEnabled: Boolean = false,
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    tileInfo: VideoTileInfo = VideoTileInfo.ESSENTIAL,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val file = remember(video.urlOrPath) { File(video.urlOrPath) }
    val extension = remember(file) { file.extension.uppercase().ifEmpty { "VID" } }
    val displayPath = remember(file) {
        file.parentFile?.parentFile?.absolutePath ?: "/storage/emulated/0"
    }

    val cleanTitle = remember(video.title) { video.title.substringBeforeLast('.') }
    val highlightColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = { if (isMultiSelectMode) onToggleSelect() else onPlay() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("local_video_card_${video.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
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
                    .align(Alignment.CenterVertically)
                    .size(120.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                VideoThumbnail(
                    videoPath = video.urlOrPath,
                    durationMs = durationMs,
                    progressMs = progressMs,
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

                // Resolution badge (Advanced mode only)
                if (tileInfo == VideoTileInfo.ADVANCED) {
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
                }

                // Progress Indicator (Advanced mode only)
                if (tileInfo == VideoTileInfo.ADVANCED && resumeEnabled && progressMs > 0L) {
                    val progressRatio = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    if (progressRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .align(Alignment.BottomCenter)
                                .testTag("video_progress_indicator_${video.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressRatio)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
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

                if (tileInfo != VideoTileInfo.MINIMAL) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Format box (extension badge, Advanced mode only)
                        if (tileInfo == VideoTileInfo.ADVANCED) {
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
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        Text(
                            text = formatDuration(video.duration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Text(
                            text = formatSize(video.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        if (tileInfo == VideoTileInfo.ADVANCED && resumeEnabled && progressMs > 0L) {
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Text(
                                text = "${formatDuration(progressMs)} / ${formatDuration(durationMs)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalVideoGridCard(
    video: VideoModel,
    resumeEnabled: Boolean = false,
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    tileInfo: VideoTileInfo = VideoTileInfo.ESSENTIAL,
    columnsCount: Int = 3,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val isDense = columnsCount >= 4
    val file = remember(video.urlOrPath) { File(video.urlOrPath) }
    val extension = remember(file) { file.extension.uppercase().ifEmpty { "VID" } }
    val parentFolder = remember(file) {
        val p = file.parentFile?.name
        if (p.isNullOrEmpty() || p == "0" || p == "emulated") "Internal Storage" else p
    }

    val cardRadius = if (isDense) 8.dp else 12.dp
    val textPaddingH = if (isDense) 4.dp else if (columnsCount == 3) 7.dp else 10.dp
    val textPaddingV = if (isDense) 4.dp else if (columnsCount == 3) 5.dp else 6.dp

    val titleFontSize = if (isDense) 11.sp else if (columnsCount == 3) 12.5.sp else 14.sp
    val titleLineHeight = if (isDense) 14.sp else if (columnsCount == 3) 16.sp else 18.sp

    val pathFontSize = if (isDense) 9.5.sp else if (columnsCount == 3) 10.5.sp else 12.sp
    val pathLineHeight = if (isDense) 12.sp else if (columnsCount == 3) 14.sp else 16.sp

    val detailFontSize = if (isDense) 9.5.sp else if (columnsCount == 3) 11.sp else 12.sp
    val detailLineHeight = if (isDense) 12.sp else if (columnsCount == 3) 14.sp else 16.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelectMode) onToggleSelect() else onPlay() },
                onLongClick = { onLongClick?.invoke() }
            )
            .testTag("local_video_grid_card_${video.id}"),
        shape = RoundedCornerShape(cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media thumbnail (16:9 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius))
            ) {
                VideoThumbnail(
                    videoPath = video.urlOrPath,
                    durationMs = durationMs,
                    progressMs = progressMs,
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
                                modifier = Modifier.size(if (isDense) 22.dp else if (columnsCount == 3) 28.dp else 36.dp)
                            )
                        }
                    }
                )

                // Overlay duration on bottom right of thumbnail (Essential & Advanced)
                if (tileInfo != VideoTileInfo.MINIMAL) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(if (isDense) 3.dp else 6.dp)
                            .clip(RoundedCornerShape(if (isDense) 3.dp else 4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = if (isDense) 3.dp else 4.dp, vertical = if (isDense) 1.dp else 2.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            color = Color.White,
                            fontSize = if (isDense) 8.5.sp else 10.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isDense) 10.sp else 12.sp
                        )
                    }
                }

                // Overlay extension & resolution badges on top left (Advanced mode only)
                if (tileInfo == VideoTileInfo.ADVANCED) {
                    val resolution = rememberVideoResolution(video)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(if (isDense) 3.dp else 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isDense) 2.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = if (isDense) 2.5.dp else 4.dp, vertical = if (isDense) 1.dp else 2.dp)
                        ) {
                            Text(
                                text = extension,
                                fontSize = if (isDense) 7.5.sp else 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                lineHeight = if (isDense) 8.sp else 10.sp
                            )
                        }

                        if (!resolution.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = if (isDense) 2.5.dp else 4.dp, vertical = if (isDense) 1.dp else 2.dp)
                            ) {
                                Text(
                                    text = resolution,
                                    fontSize = if (isDense) 7.5.sp else 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    lineHeight = if (isDense) 8.sp else 10.sp
                                )
                            }
                        }
                    }
                }

                // Progress Indicator (Advanced mode only)
                if (tileInfo == VideoTileInfo.ADVANCED && resumeEnabled && progressMs > 0L) {
                    val progressRatio = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    if (progressRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isDense) 2.5.dp else 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .align(Alignment.BottomCenter)
                                .testTag("video_progress_indicator_${video.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressRatio)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = textPaddingH, vertical = textPaddingV),
                horizontalAlignment = Alignment.Start
            ) {
                val cleanTitle = remember(video.title) { video.title.substringBeforeLast('.') }
                Text(
                    text = cleanTitle,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        lineBreak = LineBreak.Paragraph
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(if (isDense) 1.dp else 2.dp))

                Text(
                    text = parentFolder,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = pathFontSize,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = pathLineHeight,
                        textAlign = TextAlign.Start,
                        lineBreak = LineBreak.Paragraph
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (tileInfo != VideoTileInfo.MINIMAL) {
                    Spacer(modifier = Modifier.height(if (isDense) 1.dp else 2.dp))

                    val resolution = if (tileInfo == VideoTileInfo.ADVANCED) rememberVideoResolution(video) else null
                    val detailText = if (tileInfo == VideoTileInfo.ADVANCED) {
                        if (resumeEnabled && progressMs > 0L) {
                            if (isDense) {
                                "${formatSize(video.size)} • ${formatDuration(progressMs)}"
                            } else {
                                val resPart = if (!resolution.isNullOrEmpty()) " • $resolution" else ""
                                "${formatSize(video.size)} • ${formatDuration(progressMs)} / ${formatDuration(durationMs)}$resPart"
                            }
                        } else {
                            val resPart = if (!resolution.isNullOrEmpty()) " • $resolution" else ""
                            "${formatSize(video.size)}$resPart"
                        }
                    } else {
                        formatSize(video.size)
                    }

                    Text(
                        text = detailText,
                        fontSize = detailFontSize,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (tileInfo == VideoTileInfo.ADVANCED && resumeEnabled && progressMs > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = detailLineHeight,
                            fontWeight = if (tileInfo == VideoTileInfo.ADVANCED && resumeEnabled && progressMs > 0L) FontWeight.SemiBold else FontWeight.Normal,
                            lineBreak = LineBreak.Paragraph
                        )
                    )
                }
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

private data class FolderItem(
    val name: String,
    val path: String,
    val videoCount: Int,
    val totalDurationMs: Long,
    val totalSize: Long,
    val maxDate: Long,
    val sampleVideos: List<VideoModel>
)

private fun sortFolders(folders: List<FolderItem>, sortField: SortField, sortDirection: SortDirection): List<FolderItem> {
    val sorted = when (sortField) {
        SortField.NAME -> folders.sortedBy { it.name.lowercase() }
        SortField.DATE -> folders.sortedBy { it.maxDate }
        SortField.SIZE -> folders.sortedBy { it.totalSize }
        SortField.DURATION -> folders.sortedBy { it.totalDurationMs }
    }
    return if (sortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted
}

private fun sortVideos(videos: List<VideoModel>, sortField: SortField, sortDirection: SortDirection): List<VideoModel> {
    val sorted = when (sortField) {
        SortField.NAME -> videos.sortedBy { it.title.lowercase() }
        SortField.DATE -> videos.sortedBy { File(it.urlOrPath).lastModified() }
        SortField.SIZE -> videos.sortedBy { it.size }
        SortField.DURATION -> videos.sortedBy { it.duration }
    }
    return if (sortDirection == SortDirection.DESCENDING) sorted.reversed() else sorted
}

private fun resolveDeepestSingleChildPath(startPath: String, localVideos: List<VideoModel>): String {
    var currPath = startPath
    while (currPath.isNotEmpty()) {
        val hasDirectVids = localVideos.any { File(it.urlOrPath).parentFile?.absolutePath == currPath }
        if (hasDirectVids) break
        val childSubdirs = localVideos.mapNotNull { video ->
            val p = File(video.urlOrPath).parentFile ?: return@mapNotNull null
            var curr: File? = p
            var child: File? = null
            while (curr != null) {
                if (curr.absolutePath == currPath && child != null) return@mapNotNull child.absolutePath
                child = curr
                curr = curr.parentFile
            }
            null
        }.distinct()
        if (childSubdirs.size == 1) {
            currPath = childSubdirs.first()
        } else {
            break
        }
    }
    return currPath
}

private fun resolveParentBranchingPath(currPath: String, rootTreePath: String, localVideos: List<VideoModel>): String? {
    var parent = File(currPath).parentFile?.absolutePath ?: return null
    while (parent.startsWith(rootTreePath) && parent != rootTreePath) {
        val hasDirectVids = localVideos.any { File(it.urlOrPath).parentFile?.absolutePath == parent }
        if (hasDirectVids) return parent

        val childSubdirs = localVideos.mapNotNull { video ->
            val p = File(video.urlOrPath).parentFile ?: return@mapNotNull null
            var curr: File? = p
            var child: File? = null
            while (curr != null) {
                if (curr.absolutePath == parent && child != null) return@mapNotNull child.absolutePath
                child = curr
                curr = curr.parentFile
            }
            null
        }.distinct()

        if (childSubdirs.size > 1) return parent
        parent = File(parent).parentFile?.absolutePath ?: return rootTreePath
    }
    return if (parent.startsWith(rootTreePath)) rootTreePath else null
}

@Composable
fun VideoSelectionBottomBar(
    selectedCount: Int,
    isBookmarked: Boolean,
    onRename: () -> Unit,
    onFileInfo: () -> Unit,
    onBookmark: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .horizontalScroll(rememberScrollState())
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = selectedCount == 1,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(240, easing = LinearOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(240, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(180))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomActionItem(
                        icon = Icons.Default.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = onRename,
                        testTag = "action_rename"
                    )
                    BottomActionItem(
                        icon = Icons.Default.Info,
                        label = "Info",
                        onClick = onFileInfo,
                        testTag = "action_info"
                    )
                }
            }
            BottomActionItem(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isBookmarked) "Bookmarked" else "Bookmark",
                onClick = onBookmark,
                activeColor = if (isBookmarked) MaterialTheme.colorScheme.primary else null,
                testTag = "action_bookmark"
            )
            BottomActionItem(
                icon = Icons.Default.PlaylistAdd,
                label = "Playlist",
                onClick = onAddToPlaylist,
                testTag = "action_playlist"
            )
            BottomActionItem(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                onClick = onCopy,
                testTag = "action_copy"
            )
            BottomActionItem(
                icon = Icons.Default.DriveFileMove,
                label = "Move",
                onClick = onMove,
                testTag = "action_move"
            )
            BottomActionItem(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onDelete,
                isDestructive = true,
                testTag = "action_delete"
            )
        }
    }
}

@Composable
private fun BottomActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    activeColor: Color? = null,
    testTag: String = ""
) {
    val contentColor = when {
        isDestructive -> MaterialTheme.colorScheme.error
        activeColor != null -> activeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp
        )
    }
}

