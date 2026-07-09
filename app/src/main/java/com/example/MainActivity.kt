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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
                        viewModel.playPrevious()
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


        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            MyApplicationTheme(appTheme = appTheme) {
                val context = LocalContext.current
                var lastBackPressTime by remember { mutableLongStateOf(0L) }
                val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsState()

                var currentTab by remember { mutableStateOf(ScreenTab.Local) }
                var isSettingsOpen by remember { mutableStateOf(false) }
                var isMemoryOpen   by remember { mutableStateOf(false) }
                var isPlaybackSettingsOpen by remember { mutableStateOf(false) }
                var isGesturesSettingsOpen by remember { mutableStateOf(false) }

                // Back: Memory sub-screen (highest priority — checked first)
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
                BackHandler(enabled = isSettingsOpen && !isMemoryOpen && !isPlaybackSettingsOpen && !isGesturesSettingsOpen && currentPlayingVideo == null) {
                    isSettingsOpen = false
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                // Intercept back clicks on the main tabs to prevent accidental exits
                BackHandler(enabled = !isSettingsOpen && !isMemoryOpen && !isPlaybackSettingsOpen && !isGesturesSettingsOpen && currentPlayingVideo == null) {
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

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main Screen with Sub-pages & Navigation
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
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
                            NavigationBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_navigation_bar"),
                                containerColor = MaterialTheme.colorScheme.surface,
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
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                                            onNavigateToPlayer = {}
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

                    // Settings Screen Overlay
                    AnimatedVisibility(
                        visible = isSettingsOpen && currentPlayingVideo == null,
                        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("settings_screen_container")
                        ) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { isSettingsOpen = false },
                                onNavigateToMemory = { isMemoryOpen = true },
                                onNavigateToPlayback = { isPlaybackSettingsOpen = true },
                                onNavigateToGestures = { isGesturesSettingsOpen = true }
                            )
                        }
                    }

                    // Playback Settings Sub-Screen Overlay
                    AnimatedVisibility(
                        visible = isPlaybackSettingsOpen && currentPlayingVideo == null,
                        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("playback_settings_screen_container")
                        ) {
                            PlaybackSettingsScreen(
                                viewModel = viewModel,
                                onBack = { isPlaybackSettingsOpen = false }
                            )
                        }
                    }

                    // Gestures Settings Sub-Screen Overlay
                    AnimatedVisibility(
                        visible = isGesturesSettingsOpen && currentPlayingVideo == null,
                        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gestures_settings_screen_container")
                        ) {
                            GesturesSettingsScreen(
                                viewModel = viewModel,
                                onBack = { isGesturesSettingsOpen = false }
                            )
                        }
                    }

                    // Memory Sub-Screen Overlay
                    AnimatedVisibility(
                        visible = isMemoryOpen && currentPlayingVideo == null,
                        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MemoryScreen(onBack = { isMemoryOpen = false })
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
