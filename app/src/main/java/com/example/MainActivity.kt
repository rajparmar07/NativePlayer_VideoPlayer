package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import java.io.File
import com.example.viewmodel.ListDisplayMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import com.example.data.AppDatabase
import com.example.data.VideoRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoPlayerViewModel
import com.example.viewmodel.VideoPlayerViewModelFactory
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Local("Local", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    Playlists("Playlists", Icons.Filled.PlaylistPlay, Icons.Outlined.PlaylistPlay),
    Stream("Stream", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    Downloads("Downloads", Icons.Filled.OfflinePin, Icons.Outlined.OfflinePin)
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: VideoPlayerViewModel
    private var wasPausedOnLeave = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                when (action) {
                    "com.example.ACTION_PLAY_PAUSE" -> {
                        if (viewModel.isVideoPlaying.value) {
                            viewModel.pause()
                        } else {
                            viewModel.play()
                        }
                    }
                    "com.example.ACTION_NEXT" -> {
                        viewModel.playNext()
                    }
                    "com.example.ACTION_PREV" -> {
                        viewModel.playPrevious(0L, 0L)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core MVVM wiring
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(database.videoPlayerDao())
        val factory = VideoPlayerViewModelFactory(repository, applicationContext)
        val viewModelInstance: VideoPlayerViewModel by viewModels { factory }
        viewModel = viewModelInstance

        // Register PiP control broadcasts
        val filter = IntentFilter().apply {
            addAction("com.example.ACTION_PLAY_PAUSE")
            addAction("com.example.ACTION_NEXT")
            addAction("com.example.ACTION_PREV")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }
        }

        lifecycleScope.launch {
            viewModel.isVideoPlaying.collectLatest { playing ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                    updatePipParams(playing)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPlayingVideo.collectLatest { video ->
                if (video == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                    finish()
                }
            }
        }


        @OptIn(ExperimentalMaterial3Api::class)
        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val appPalette by viewModel.appThemePalette.collectAsState()
            val appFontSize by viewModel.appFontSize.collectAsState()
            val isHighContrastDark by viewModel.isHighContrastDark.collectAsState()

            MyApplicationTheme(
                appTheme = appTheme,
                appPalette = appPalette,
                appFontSize = appFontSize,
                isHighContrastDark = isHighContrastDark
            ) {
                val context = LocalContext.current
                var lastBackPressTime by remember { mutableLongStateOf(0L) }
                val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsState()

                var currentTab by remember { mutableStateOf(ScreenTab.Local) }
                var isSettingsOpen by remember { mutableStateOf(false) }
                var isAppearanceSettingsOpen by remember { mutableStateOf(false) }
                var isAudioSettingsOpen by remember { mutableStateOf(false) }
                var isMemoryOpen   by remember { mutableStateOf(false) }
                var isPlaybackSettingsOpen by remember { mutableStateOf(false) }
                var isGesturesSettingsOpen by remember { mutableStateOf(false) }

                // Back: Appearance settings sub-screen (highest priority)
                BackHandler(enabled = isAppearanceSettingsOpen && currentPlayingVideo == null) {
                    isAppearanceSettingsOpen = false
                }

                // Back: Audio settings sub-screen
                BackHandler(enabled = isAudioSettingsOpen && currentPlayingVideo == null) {
                    isAudioSettingsOpen = false
                }

                // Back: Memory sub-screen
                BackHandler(enabled = isMemoryOpen && currentPlayingVideo == null) {
                    isMemoryOpen = false
                }

                // Back: Playback settings sub-screen
                BackHandler(enabled = isPlaybackSettingsOpen && currentPlayingVideo == null) {
                    isPlaybackSettingsOpen = false
                }

                // Back: Gestures settings sub-screen
                BackHandler(enabled = isGesturesSettingsOpen && currentPlayingVideo == null) {
                    isGesturesSettingsOpen = false
                }

                // Back: Settings screen
                BackHandler(enabled = isSettingsOpen && !isAppearanceSettingsOpen && !isAudioSettingsOpen && !isMemoryOpen && !isPlaybackSettingsOpen && !isGesturesSettingsOpen && currentPlayingVideo == null) {
                    isSettingsOpen = false
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                // Intercept back clicks on the main tabs to prevent accidental exits
                BackHandler(enabled = !isSettingsOpen && !isAppearanceSettingsOpen && !isAudioSettingsOpen && !isMemoryOpen && !isPlaybackSettingsOpen && !isGesturesSettingsOpen && currentPlayingVideo == null) {
                    val currentTime = System.currentTimeMillis()
                    val activity = context as? Activity
                    if (currentTime - lastBackPressTime < 2000) {
                        activity?.finish()
                        activity?.overridePendingTransition(0, android.R.anim.fade_out)
                    } else {
                        lastBackPressTime = currentTime
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Press back again to exit")
                        }
                    }
                }

                val isSettingsActive = (isSettingsOpen || isAppearanceSettingsOpen || isAudioSettingsOpen || isPlaybackSettingsOpen || isGesturesSettingsOpen || isMemoryOpen) && currentPlayingVideo == null
                var createPlaylistListener by remember { mutableStateOf<(() -> Unit)?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main Screen with Sub-pages & Navigation
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            val selectedVideoIds by viewModel.selectedVideoIds.collectAsState()
                            val selectedFolder by viewModel.selectedFolder.collectAsState()
                            val selectedTreePath by viewModel.selectedTreePath.collectAsState()
                            val displaySettings by viewModel.displaySettings.collectAsState()
                            val localVideos by viewModel.localVideos.collectAsState()
                            val isSearchingMode by viewModel.isSearchingMode.collectAsState()
                            val groupedVideos = remember(localVideos) { localVideos.groupBy { File(it.urlOrPath).parentFile?.name ?: "Internal Memory" } }

                            val rootTreePath = remember(localVideos) { viewModel.getRootTreePath() }
                            val currentEffectiveTreePath = remember(selectedTreePath, rootTreePath) { selectedTreePath ?: rootTreePath }
                            val canGoBackTree = remember(selectedTreePath, rootTreePath) {
                                val path = selectedTreePath
                                path != null && path != rootTreePath && path != "/storage/emulated/0"
                            }

                            val showLocalBack = if (displaySettings.displayMode == ListDisplayMode.FOLDERS) selectedFolder != null else canGoBackTree

                            val localTitle = remember(selectedVideoIds, selectedFolder, displaySettings.displayMode, selectedTreePath, currentEffectiveTreePath, localVideos, isSearchingMode) {
                                if (isSearchingMode) {
                                    "Search Videos"
                                } else if (selectedVideoIds.isNotEmpty()) {
                                    val currentList = if (selectedFolder != null) (groupedVideos[selectedFolder] ?: emptyList()) else localVideos
                                    "${selectedVideoIds.size} / ${currentList.size} Selected"
                                } else if (displaySettings.displayMode == ListDisplayMode.FOLDERS) {
                                    selectedFolder ?: "Local Media"
                                } else {
                                    val rawName = if (selectedTreePath != null) File(selectedTreePath!!).name else File(currentEffectiveTreePath).name
                                    if (rawName == "0" || rawName.isBlank() || rawName == "emulated") "Internal Storage" else rawName
                                }
                            }

                            val localSubtitle: String? = remember(selectedVideoIds, selectedFolder, displaySettings.displayMode, currentEffectiveTreePath, rootTreePath, localVideos, groupedVideos, isSearchingMode) {
                                if (isSearchingMode) {
                                    "Find local video files by name or path"
                                } else if (selectedVideoIds.isNotEmpty()) {
                                    null
                                } else if (displaySettings.displayMode == ListDisplayMode.FOLDERS) {
                                    if (selectedFolder != null) {
                                        val count = groupedVideos[selectedFolder]?.size ?: 0
                                        if (count == 1) "1 video in folder" else "$count videos in folder"
                                    } else {
                                        val count = localVideos.size
                                        if (count == 1) "1 video on device" else "$count videos on device"
                                    }
                                } else {
                                    val path = currentEffectiveTreePath
                                    if (path == rootTreePath || path == "/storage/emulated/0") {
                                        "Internal Storage Root"
                                    } else {
                                        path.replace("/storage/emulated/0", "Internal Storage")
                                    }
                                }
                            }

                            StreamCacheTopBar(
                                title = when (currentTab) {
                                    ScreenTab.Local -> localTitle
                                    ScreenTab.Playlists -> "Playlists"
                                    ScreenTab.Stream -> "Network Stream"
                                    ScreenTab.Downloads -> "Downloads"
                                },
                                subtitle = when (currentTab) {
                                    ScreenTab.Local -> localSubtitle
                                    ScreenTab.Playlists -> "Create and organize video collections"
                                    ScreenTab.Stream -> "Play online URLs & live streams"
                                    ScreenTab.Downloads -> "Download videos from links and play offline"
                                },
                                navigationIcon = if (currentTab == ScreenTab.Local) {
                                    if (isSearchingMode) {
                                        {
                                            IconButton(
                                                onClick = { viewModel.setIsSearchingMode(false) },
                                                modifier = Modifier.testTag("exit_search_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowBack,
                                                    contentDescription = "Exit Search",
                                                    tint = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    } else if (selectedVideoIds.isNotEmpty()) {
                                        {
                                            val currentList = if (selectedFolder != null) (groupedVideos[selectedFolder] ?: emptyList()) else localVideos
                                            val allSelected = currentList.isNotEmpty() && selectedVideoIds.size == currentList.size
                                            Checkbox(
                                                checked = allSelected,
                                                onCheckedChange = { checked ->
                                                    viewModel.setSelectedVideoIds(if (checked) currentList.map { it.id }.toSet() else emptySet())
                                                },
                                                modifier = Modifier.testTag("select_all_checkbox")
                                            )
                                        }
                                    } else if (showLocalBack) {
                                        {
                                            IconButton(
                                                onClick = {
                                                    if (displaySettings.displayMode == ListDisplayMode.FOLDERS) {
                                                        viewModel.setSelectedFolder(null)
                                                    } else {
                                                        val targetParent = viewModel.resolveParentBranchingPath(selectedTreePath!!, rootTreePath, localVideos)
                                                        if (targetParent != null && targetParent != selectedTreePath && targetParent != rootTreePath) {
                                                            viewModel.setSelectedTreePath(targetParent)
                                                        } else {
                                                            viewModel.setSelectedTreePath(null)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.testTag("local_back_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowBack,
                                                    contentDescription = "Back",
                                                    tint = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    } else null
                                } else null,
                                actions = {
                                    // Bulk Action Buttons (visible when selecting)
                                    AnimatedVisibility(
                                        visible = currentTab == ScreenTab.Local && selectedVideoIds.isNotEmpty(),
                                        enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                                        exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeOut(tween(180)) + scaleOut(targetScale = 0.85f)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.setShowBulkCopyDialog(true) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                    .testTag("bulk_copy_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Bulk Copy",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.setShowBulkMoveDialog(true) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                    .testTag("bulk_move_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DriveFileMove,
                                                    contentDescription = "Bulk Move",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.setShowBulkDeleteDialog(true) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                    .testTag("bulk_delete_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Bulk Delete",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    // Tune Button (visible ONLY on Local tab when not selecting and not searching)
                                    AnimatedVisibility(
                                        visible = currentTab == ScreenTab.Local && selectedVideoIds.isEmpty() && !isSearchingMode,
                                        enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                                        exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeOut(tween(180)) + scaleOut(targetScale = 0.85f)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.setShowDisplaySettingsDialog(true) },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                .testTag("toggle_layout_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Display & Layout Settings",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // New Playlist Button (visible ONLY on Playlists tab)
                                    AnimatedVisibility(
                                        visible = currentTab == ScreenTab.Playlists,
                                        enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                                        exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeOut(tween(180)) + scaleOut(targetScale = 0.85f)
                                    ) {
                                        IconButton(
                                            onClick = { createPlaylistListener?.invoke() },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                .testTag("new_playlist_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistAdd,
                                                contentDescription = "New Playlist",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Persistent Settings Button — ALWAYS Present, ALWAYS Stationary!
                                    IconButton(
                                        onClick = { isSettingsOpen = true },
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .testTag("settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        },
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                snackbar = { snackbarData ->
                                    Card(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = snackbarData.visuals.message,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            val defaultNavColor = NavigationBarDefaults.containerColor
                            val isDarkTheme = (MaterialTheme.colorScheme.background.red * 0.299f + MaterialTheme.colorScheme.background.green * 0.587f + MaterialTheme.colorScheme.background.blue * 0.114f) < 0.5f
                            val barBackgroundColor = if (isDarkTheme) defaultNavColor else Color.White

                            NavigationBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_navigation_bar"),
                                containerColor = barBackgroundColor,
                                tonalElevation = 8.dp
                            ) {
                                ScreenTab.entries.forEach { tab ->
                                    val isSelected = currentTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title
                                            )
                                        },
                                        label = { Text(text = tab.title) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = Color.Transparent,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding())
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    val isForward = targetState.ordinal > initialState.ordinal
                                    if (isForward) {
                                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it }) + fadeOut(animationSpec = tween(300)))
                                    } else {
                                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)))
                                    }
                                },
                                label = "tab_switch_animation",
                                modifier = Modifier.fillMaxSize()
                            ) { targetTab ->
                                when (targetTab) {
                                    ScreenTab.Local -> {
                                        LocalLibraryScreen(
                                            viewModel = viewModel,
                                            onNavigateToPlayer = {},
                                            onNavigateToSettings = { isSettingsOpen = true }
                                        )
                                    }
                                    ScreenTab.Playlists -> {
                                        PlaylistScreen(
                                            viewModel = viewModel,
                                            onNavigateToPlayer = {},
                                            onCreatePlaylistRequested = { listener -> createPlaylistListener = listener }
                                        )
                                    }
                                    ScreenTab.Stream -> {
                                        StreamScreen(
                                            viewModel = viewModel,
                                            onNavigateToPlayer = {},
                                            onNavigateToDownloads = {
                                                currentTab = ScreenTab.Downloads
                                            },
                                            onNavigateToLocal = {
                                                currentTab = ScreenTab.Local
                                            },
                                            onNavigateToPlaylists = {
                                                currentTab = ScreenTab.Playlists
                                            }
                                        )
                                    }
                                    ScreenTab.Downloads -> {
                                        DownloadsScreen(
                                            viewModel = viewModel,
                                            onNavigateToPlayer = {}
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Settings Overlay Container (Hides bottom navigation bar while active)
                    AnimatedVisibility(
                        visible = isSettingsActive,
                        enter = fadeIn(animationSpec = tween(250)),
                        exit = fadeOut(animationSpec = tween(250))
                    ) {
                        val activeSubScreen = remember(isAppearanceSettingsOpen, isAudioSettingsOpen, isPlaybackSettingsOpen, isGesturesSettingsOpen, isMemoryOpen) {
                            when {
                                isAppearanceSettingsOpen -> "Appearance"
                                isAudioSettingsOpen      -> "Audio"
                                isPlaybackSettingsOpen   -> "Playback"
                                isGesturesSettingsOpen   -> "Gestures"
                                isMemoryOpen             -> "Memory & Cache"
                                else                     -> "Settings"
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("settings_screen_container"),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // STATIONARY FIXED TOP BAR (EXACT SAME UNIFORM SIZE & PADDING)
                                StreamCacheTopBar(
                                    title = activeSubScreen,
                                    navigationIcon = {
                                        IconButton(
                                            onClick = {
                                                when {
                                                    isAppearanceSettingsOpen -> isAppearanceSettingsOpen = false
                                                    isAudioSettingsOpen      -> isAudioSettingsOpen = false
                                                    isPlaybackSettingsOpen   -> isPlaybackSettingsOpen = false
                                                    isGesturesSettingsOpen   -> isGesturesSettingsOpen = false
                                                    isMemoryOpen             -> isMemoryOpen = false
                                                    else                     -> isSettingsOpen = false
                                                }
                                            },
                                            modifier = Modifier.testTag("settings_unified_back_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Back",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                )

                                // SLIDE TRANSITION FOR BODY CONTENT ONLY BELOW THE TOP BAR
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    AnimatedContent(
                                        targetState = activeSubScreen,
                                        transitionSpec = {
                                            val isNavigatingToSubScreen = targetState != "Settings" && initialState == "Settings"
                                            if (isNavigatingToSubScreen) {
                                                (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)))
                                                    .togetherWith(slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it / 3 }) + fadeOut(animationSpec = tween(300)))
                                            } else {
                                                (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it / 3 }) + fadeIn(animationSpec = tween(300)))
                                                    .togetherWith(slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)))
                                            }
                                        },
                                        label = "settings_body_content_transition",
                                        modifier = Modifier.fillMaxSize()
                                    ) { currentSubScreen ->
                                        when (currentSubScreen) {
                                            "Settings" -> {
                                                SettingsScreen(
                                                    viewModel = viewModel,
                                                    onBack = { isSettingsOpen = false },
                                                    onNavigateToAppearance = { isAppearanceSettingsOpen = true },
                                                    onNavigateToAudio = { isAudioSettingsOpen = true },
                                                    onNavigateToMemory = { isMemoryOpen = true },
                                                    onNavigateToPlayback = { isPlaybackSettingsOpen = true },
                                                    onNavigateToGestures = { isGesturesSettingsOpen = true },
                                                    includeTopBar = false
                                                )
                                            }
                                            "Appearance" -> {
                                                AppearanceSettingsScreen(
                                                    viewModel = viewModel,
                                                    onBack = { isAppearanceSettingsOpen = false },
                                                    includeTopBar = false
                                                )
                                            }
                                            "Audio" -> {
                                                AudioSettingsScreen(
                                                    viewModel = viewModel,
                                                    onBack = { isAudioSettingsOpen = false },
                                                    includeTopBar = false
                                                )
                                            }
                                            "Playback" -> {
                                                PlaybackSettingsScreen(
                                                    viewModel = viewModel,
                                                    onBack = { isPlaybackSettingsOpen = false },
                                                    includeTopBar = false
                                                )
                                            }
                                            "Gestures" -> {
                                                GesturesSettingsScreen(
                                                    viewModel = viewModel,
                                                    onBack = { isGesturesSettingsOpen = false },
                                                    includeTopBar = false
                                                )
                                            }
                                            "Memory & Cache" -> {
                                                MemoryScreen(
                                                    onBack = { isMemoryOpen = false },
                                                    includeTopBar = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Immersive Fullscreen Video Player Mode (Overlays completely to suppress notches and insets)
                    AnimatedVisibility(
                        visible = currentPlayingVideo != null,
                        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fullscreen_player_container")
                        ) {
                            PlayerScreen(
                                viewModel = viewModel,
                                onBack = {
                                    // Managed inside PlayerScreen with BackHandler but has clean escape
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updatePipParams(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = ArrayList<RemoteAction>()

            // Prev Action
            val prevIntent = Intent("com.example.ACTION_PREV")
            val prevPendingIntent = PendingIntent.getBroadcast(
                this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevIcon = Icon.createWithResource(this, android.R.drawable.ic_media_previous)
            val prevAction = RemoteAction(prevIcon, "Previous", "Previous video", prevPendingIntent)
            actions.add(prevAction)

            // Play/Pause Action
            val playPauseIntent = Intent("com.example.ACTION_PLAY_PAUSE")
            val playPausePendingIntent = PendingIntent.getBroadcast(
                this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIcon = Icon.createWithResource(
                this,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            val playPauseAction = RemoteAction(
                playPauseIcon,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause video" else "Play video",
                playPausePendingIntent
            )
            actions.add(playPauseAction)

            // Next Action
            val nextIntent = Intent("com.example.ACTION_NEXT")
            val nextPendingIntent = PendingIntent.getBroadcast(
                this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIcon = Icon.createWithResource(this, android.R.drawable.ic_media_next)
            val nextAction = RemoteAction(nextIcon, "Next", "Next video", nextPendingIntent)
            actions.add(nextAction)

            val width = viewModel.videoWidth.value
            val height = viewModel.videoHeight.value
            val rational = try {
                val r = Rational(width, height)
                if (r.toFloat() in 0.418f..2.39f) r else Rational(16, 9)
            } catch (e: Exception) {
                Rational(16, 9)
            }

            val params = PictureInPictureParams.Builder()
                .setActions(actions)
                .setAspectRatio(rational)
                .build()
            setPictureInPictureParams(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isVideoPlaying = viewModel.isVideoPlaying.value
        val currentVideo = viewModel.currentPlayingVideo.value
        val isPipEnabled = viewModel.pipEnabled.value

        if (currentVideo != null && isVideoPlaying && isPipEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actions = ArrayList<RemoteAction>()

                val prevIntent = Intent("com.example.ACTION_PREV")
                val prevPendingIntent = PendingIntent.getBroadcast(
                    this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val prevIcon = Icon.createWithResource(this, android.R.drawable.ic_media_previous)
                val prevAction = RemoteAction(prevIcon, "Previous", "Previous video", prevPendingIntent)
                actions.add(prevAction)

                val playPauseIntent = Intent("com.example.ACTION_PLAY_PAUSE")
                val playPausePendingIntent = PendingIntent.getBroadcast(
                    this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val playPauseIcon = Icon.createWithResource(this, android.R.drawable.ic_media_pause)
                val playPauseAction = RemoteAction(playPauseIcon, "Pause", "Pause video", playPausePendingIntent)
                actions.add(playPauseAction)

                val nextIntent = Intent("com.example.ACTION_NEXT")
                val nextPendingIntent = PendingIntent.getBroadcast(
                    this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val nextIcon = Icon.createWithResource(this, android.R.drawable.ic_media_next)
                val nextAction = RemoteAction(nextIcon, "Next", "Next video", nextPendingIntent)
                actions.add(nextAction)

                val width = viewModel.videoWidth.value
                val height = viewModel.videoHeight.value
                val rational = try {
                    val r = Rational(width, height)
                    if (r.toFloat() in 0.418f..2.39f) r else Rational(16, 9)
                } catch (e: Exception) {
                    Rational(16, 9)
                }

                val params = PictureInPictureParams.Builder()
                    .setActions(actions)
                    .setAspectRatio(rational)
                    .build()
                enterPictureInPictureMode(params)
            }
        } else if (currentVideo != null && isVideoPlaying && !isPipEnabled) {
            viewModel.pause()
            wasPausedOnLeave = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasPausedOnLeave) {
            wasPausedOnLeave = false
            viewModel.play()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPipMode(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
    }
}

@Composable
fun StreamCacheTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    val defaultNavColor = NavigationBarDefaults.containerColor
    val isDarkTheme = (MaterialTheme.colorScheme.background.red * 0.299f + MaterialTheme.colorScheme.background.green * 0.587f + MaterialTheme.colorScheme.background.blue * 0.114f) < 0.5f
    val barBackgroundColor = if (isDarkTheme) defaultNavColor else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = barBackgroundColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val emphasizedEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }

                AnimatedVisibility(
                    visible = navigationIcon != null,
                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(300, easing = emphasizedEasing)) + fadeIn(tween(250)) + scaleIn(initialScale = 0.85f),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(300, easing = emphasizedEasing)) + fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
                ) {
                    if (navigationIcon != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            navigationIcon()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                AnimatedContent(
                    targetState = Pair(title, subtitle ?: ""),
                    transitionSpec = {
                        (slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(300, easing = emphasizedEasing)) + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300, easing = emphasizedEasing)) + fadeOut(tween(200)))
                    },
                    label = "topbar_slide_morph"
                ) { (currentTitle, currentSubtitle) ->
                    Column {
                        Text(
                            text = currentTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (currentSubtitle.isNotEmpty()) {
                            Text(
                                text = currentSubtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions()
            }
        }
    }
}
