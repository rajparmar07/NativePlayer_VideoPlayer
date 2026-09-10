package com.nativeplayer.videoplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OpenSourceLibrary(
    val name: String,
    val artifact: String,
    val version: String,
    val license: String,
    val description: String,
    val url: String,
    val category: String
)

object OpenSourceLibrariesData {
    val libraries = listOf(
        OpenSourceLibrary(
            name = "AndroidX Media3 ExoPlayer",
            artifact = "androidx.media3:media3-exoplayer",
            version = "1.5.0",
            license = "Apache 2.0",
            description = "High-performance, extensible media player for Android with DASH, HLS, SmoothStreaming, and progressive playback.",
            url = "https://github.com/androidx/media",
            category = "Media & Playback"
        ),
        OpenSourceLibrary(
            name = "AndroidX Media3 UI",
            artifact = "androidx.media3:media3-ui",
            version = "1.5.0",
            license = "Apache 2.0",
            description = "Material Design UI components and PlayerView controllers for AndroidX Media3.",
            url = "https://github.com/androidx/media",
            category = "Media & Playback"
        ),
        OpenSourceLibrary(
            name = "AndroidX Media3 HLS",
            artifact = "androidx.media3:media3-exoplayer-hls",
            version = "1.5.0",
            license = "Apache 2.0",
            description = "HTTP Live Streaming (HLS) extension module for media playback.",
            url = "https://github.com/androidx/media",
            category = "Media & Playback"
        ),
        OpenSourceLibrary(
            name = "Jellyfin Media3 FFmpeg Decoder",
            artifact = "org.jellyfin.media3:media3-ffmpeg-decoder",
            version = "1.5.0+1",
            license = "LGPL 2.1",
            description = "FFmpeg software audio & video decoders for advanced codec compatibility (MKV, AC3, DTS, EAC3).",
            url = "https://github.com/jellyfin/jellyfin-media3-ffmpeg-decoder",
            category = "Media & Playback"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose UI & Foundation",
            artifact = "androidx.compose.ui:ui",
            version = "2024.09.00",
            license = "Apache 2.0",
            description = "Android's modern declarative toolkit for building native UI.",
            url = "https://developer.android.com/jetpack/compose",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose Material 3",
            artifact = "androidx.compose.material3:material3",
            version = "2024.09.00",
            license = "Apache 2.0",
            description = "Material You components, dynamic color theming, and modern design system.",
            url = "https://developer.android.com/jetpack/compose/material3",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose Material Icons",
            artifact = "androidx.compose.material:material-icons-extended",
            version = "2024.09.00",
            license = "Apache 2.0",
            description = "Comprehensive library of Material Design vector icons.",
            url = "https://developer.android.com/jetpack/compose",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "AndroidX Navigation Compose",
            artifact = "androidx.navigation:navigation-compose",
            version = "2.8.9",
            license = "Apache 2.0",
            description = "Navigation component designed specifically for Jetpack Compose apps.",
            url = "https://developer.android.com/jetpack/androidx/releases/navigation",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "AndroidX Activity Compose",
            artifact = "androidx.activity:activity-compose",
            version = "1.9.3",
            license = "Apache 2.0",
            description = "Compose integration with Android Activity and back press handlers.",
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "AndroidX Room Database",
            artifact = "androidx.room:room-runtime / room-ktx",
            version = "2.7.0",
            license = "Apache 2.0",
            description = "Robust SQLite object mapping library with coroutines & Flow support.",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
            category = "Storage & Database"
        ),
        OpenSourceLibrary(
            name = "AndroidX DataStore Preferences",
            artifact = "androidx.datastore:datastore-preferences",
            version = "1.1.7",
            license = "Apache 2.0",
            description = "Modern, asynchronous transactional data storage solution replacing SharedPreferences.",
            url = "https://developer.android.com/jetpack/androidx/releases/datastore",
            category = "Storage & Database"
        ),
        OpenSourceLibrary(
            name = "Coil Compose",
            artifact = "io.coil-kt:coil-compose",
            version = "2.7.0",
            license = "Apache 2.0",
            description = "Fast, lightweight image loading library for Kotlin and Jetpack Compose.",
            url = "https://github.com/coil-kt/coil",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "Square Retrofit",
            artifact = "com.squareup.retrofit2:retrofit",
            version = "2.12.0",
            license = "Apache 2.0",
            description = "Type-safe HTTP client for Android and Kotlin.",
            url = "https://github.com/square/retrofit",
            category = "Networking"
        ),
        OpenSourceLibrary(
            name = "Square OkHttp & Logging Interceptor",
            artifact = "com.squareup.okhttp3:okhttp",
            version = "4.10.0",
            license = "Apache 2.0",
            description = "Fast, reliable HTTP/2 client for Android network communications.",
            url = "https://github.com/square/okhttp",
            category = "Networking"
        ),
        OpenSourceLibrary(
            name = "Square Moshi Kotlin",
            artifact = "com.squareup.moshi:moshi-kotlin",
            version = "1.15.2",
            license = "Apache 2.0",
            description = "Modern JSON parsing library for Kotlin and Android.",
            url = "https://github.com/square/moshi",
            category = "Networking"
        ),
        OpenSourceLibrary(
            name = "Kotlinx Coroutines",
            artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-android",
            version = "1.10.2",
            license = "Apache 2.0",
            description = "Asynchronous, non-blocking programming framework for Kotlin.",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            category = "Architecture & Async"
        ),
        OpenSourceLibrary(
            name = "AndroidX Lifecycle & ViewModel Compose",
            artifact = "androidx.lifecycle:lifecycle-runtime-compose",
            version = "2.8.7",
            license = "Apache 2.0",
            description = "Lifecycle-aware components, ViewModels, and StateFlow integration for Compose.",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            category = "Architecture & Async"
        ),
        OpenSourceLibrary(
            name = "AndroidX Core KTX",
            artifact = "androidx.core:core-ktx",
            version = "1.13.0",
            license = "Apache 2.0",
            description = "Kotlin extensions for core Android framework APIs.",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
            category = "Architecture & Async"
        ),
        OpenSourceLibrary(
            name = "Google Accompanist Permissions",
            artifact = "com.google.accompanist:accompanist-permissions",
            version = "0.37.3",
            license = "Apache 2.0",
            description = "Utilities for handling Android runtime permissions cleanly in Jetpack Compose.",
            url = "https://github.com/google/accompanist",
            category = "UI & Compose"
        ),
        OpenSourceLibrary(
            name = "AndroidX CameraX",
            artifact = "androidx.camera:camera-camera2",
            version = "1.5.0",
            license = "Apache 2.0",
            description = "Consistent and easy-to-use camera API suite across Android devices.",
            url = "https://developer.android.com/jetpack/androidx/releases/camera",
            category = "Media & Playback"
        ),
        OpenSourceLibrary(
            name = "Google Play Services Location",
            artifact = "com.google.android.gms:play-services-location",
            version = "21.3.0",
            license = "Android SDK License",
            description = "Google Play services location and geolocation APIs.",
            url = "https://developers.google.com/android/guides/overview",
            category = "Utilities"
        ),
        OpenSourceLibrary(
            name = "Robolectric",
            artifact = "org.robolectric:robolectric",
            version = "4.16.1",
            license = "MIT",
            description = "Industry standard Android unit testing framework running on JVM.",
            url = "https://github.com/robolectric/robolectric",
            category = "Testing"
        ),
        OpenSourceLibrary(
            name = "Roborazzi",
            artifact = "io.github.takahirom.roborazzi:roborazzi",
            version = "1.59.0",
            license = "Apache 2.0",
            description = "Automated screenshot testing tool for Android and Jetpack Compose.",
            url = "https://github.com/takahirom/roborazzi",
            category = "Testing"
        ),
        OpenSourceLibrary(
            name = "JUnit 4",
            artifact = "junit:junit",
            version = "4.13.2",
            license = "EPL 1.0",
            description = "Simple framework to write repeatable tests in Java and Kotlin.",
            url = "https://junit.org/junit4/",
            category = "Testing"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLibrariesScreen(
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    includeTopBar: Boolean = true
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All") + OpenSourceLibrariesData.libraries.map { it.category }.distinct()
    }

    val filteredLibraries = remember(selectedCategory) {
        if (selectedCategory == "All") {
            OpenSourceLibrariesData.libraries
        } else {
            OpenSourceLibrariesData.libraries.filter { it.category == selectedCategory }
        }
    }

    val bodyContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            borderWidth = 1.dp
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("filter_chip_$category")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // Library Cards List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("open_source_libraries_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp + navBarBottom)
            ) {
                items(filteredLibraries, key = { it.artifact }) { library ->
                    LibraryCard(
                        library = library,
                        onOpenUrl = { url -> openLibraryUrl(context, url, onShowSnackbar) }
                    )
                }
            }
        }
    }

    if (includeTopBar) {
        Scaffold(
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Open Source Libraries",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("libraries_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            bodyContent(innerPadding)
        }
    } else {
        bodyContent(PaddingValues(0.dp))
    }
}

@Composable
private fun LibraryCard(
    library: OpenSourceLibrary,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_card_${library.name.lowercase().replace(' ', '_')}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header: Name + License Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = library.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = library.license,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Artifact Identifier & Version Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = library.artifact,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "v${library.version}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Description
            Text(
                text = library.description,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Category & Web Link Action Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = library.category,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedButton(
                    onClick = { onOpenUrl(library.url) },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Link",
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Website / GitHub",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun openLibraryUrl(context: Context, url: String, onShowSnackbar: (String) -> Unit = {}) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        onShowSnackbar("Could not open browser link")
    }
}
