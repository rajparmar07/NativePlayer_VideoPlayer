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
import com.example.data.AppDatabase
import com.example.data.VideoRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VideoPlayerViewModel
import com.example.viewmodel.VideoPlayerViewModelFactory

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core MVVM wiring
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(database.videoPlayerDao())
        val factory = VideoPlayerViewModelFactory(repository, applicationContext)
        val viewModel: VideoPlayerViewModel by viewModels { factory }

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            MyApplicationTheme(appTheme = appTheme) {
                val context = LocalContext.current
                var lastBackPressTime by remember { mutableLongStateOf(0L) }
                val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsState()

                var currentTab by remember { mutableStateOf(ScreenTab.Local) }
                var isSettingsOpen by remember { mutableStateOf(false) }

                // Intercept back clicks in Settings screen to exit settings
                BackHandler(enabled = isSettingsOpen && currentPlayingVideo == null) {
                    isSettingsOpen = false
                }

                // Intercept back clicks on the main tabs to prevent accidental exits
                BackHandler(enabled = !isSettingsOpen && currentPlayingVideo == null) {
                    val currentTime = System.currentTimeMillis()
                    val activity = context as? Activity
                    if (currentTime - lastBackPressTime < 2000) {
                        activity?.finish()
                        activity?.overridePendingTransition(0, android.R.anim.fade_out)
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main Screen with Sub-pages & Navigation
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_navigation_bar")
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
                            when (currentTab) {
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
                                onBack = { isSettingsOpen = false }
                            )
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
}
