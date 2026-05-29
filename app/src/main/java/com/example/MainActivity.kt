package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
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
        val factory = VideoPlayerViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                // Fetch the active ViewModel instance
                val viewModel: VideoPlayerViewModel by viewModels { factory }
                val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsState()

                var currentTab by remember { mutableStateOf(ScreenTab.Local) }

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
                                ScreenTab.values().forEach { tab ->
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
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                ScreenTab.Local -> {
                                    LocalLibraryScreen(
                                        viewModel = viewModel,
                                        onNavigateToPlayer = {}
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

                    // Immersive Fullscreen Video Player Mode (Overlays completely to suppress notches and insets)
                    if (currentPlayingVideo != null) {
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
