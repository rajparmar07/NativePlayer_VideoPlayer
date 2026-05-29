package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Playlist
import com.example.data.VideoModel
import com.example.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.launch

data class TestStream(
    val category: String,
    val title: String,
    val url: String,
    val subtitleUrl: String? = null
)

@Composable
fun StreamScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val localVideos by viewModel.localVideos.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var subtitleInput by remember { mutableStateOf("") }

    var showPlaylistDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listScrollState = rememberScrollState()

    // Popular open-source test streams to simplify evaluation
    val testStreams = remember {
        listOf(
            TestStream(
                category = "HLS Stream",
                title = "Big Buck Bunny HLS",
                url = "https://test-streams.mux.dev/x36xhg/main.m3u8"
            ),
            TestStream(
                category = "Trailer",
                title = "Sintel Trailer 1080p",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                subtitleUrl = "https://raw.githubusercontent.com/andrey-orlov/webvtt-test/master/sintel-en.vtt"
            ),
            TestStream(
                category = "Live Stream",
                title = "Tears of Steel HLS",
                url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
            ),
            TestStream(
                category = "Quick Test",
                title = "For Bigger Blazes (Short)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)) // Deep dark slate background
            .testTag("stream_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(listScrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Immersive Top Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("immersive_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(8.dp, CircleShape)
                            .background(Color(0xFF2563EB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Launcher Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "NeoPlayer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Security Verified Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Verified connection icon",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "ENCRYPTED",
                        color = Color(0xFF34D399),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Hero Section: Immersive Now Playing Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.777f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                    .testTag("hero_stream_card")
            ) {
                // Dimmer overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 100f
                            )
                        )
                )

                // Large Video Placeholder Center Asset
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(72.dp)
                    )
                }

                // Content Overlay bottom leading
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEF4444))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Global E-Sports Finals",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "4.2k Viewers • 1080p60",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // Floating Round play button
                    IconButton(
                        onClick = {
                            val liveVideo = VideoModel(
                                id = "live_esports_banner",
                                title = "Global E-Sports Finals",
                                urlOrPath = "https://test-streams.mux.dev/x36xhg/main.m3u8",
                                isStream = true
                            )
                            viewModel.playVideo(liveVideo)
                            onNavigateToPlayer()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape)
                            .background(Color(0xFF3B82F6), CircleShape)
                            .testTag("hero_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Live Stream",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Features Grid (Bento Style within Immersive Theme)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bento 1: Stream from URL (Full width)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                listScrollState.animateScrollTo(1000)
                            }
                        }
                        .testTag("bento_stream_shortcut"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF6366F1).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8)
                                )
                            }
                            Column {
                                Text(
                                    text = "Stream from URL",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Enter .m3u8 or server link",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF475569)
                        )
                    }
                }

                // Bento Row (2 half-width files)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left half: Local Media
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp)
                            .clickable { onNavigateToLocal() }
                            .testTag("bento_local_shortcut"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Local Media",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (localVideos.isEmpty()) "0 Videos" else "${localVideos.size} Videos",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Right half: Offline Downloads
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(104.dp)
                            .clickable { onNavigateToDownloads() }
                            .testTag("bento_offline_shortcut"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Offline",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (downloads.isEmpty()) "0 Videos" else "${downloads.size} saved",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Bento 4: Playlists Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlaylists() }
                        .testTag("bento_playlist_shortcut"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Fancy overlapping avatars illustration
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-16).dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF475569))
                                        .border(2.dp, Color(0xFF1A1C1E), RoundedCornerShape(8.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF334155))
                                        .border(2.dp, Color(0xFF1A1C1E), RoundedCornerShape(8.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(2.dp, Color(0xFF1A1C1E), RoundedCornerShape(8.dp))
                                )
                            }

                            Column {
                                Text(
                                    text = "Playlists",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${playlists.size} collections",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { onNavigateToPlaylists() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "View All",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preset Test Streams Card slider
            Text(
                text = "Preset Testing Streams",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF3B82F6),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(testStreams) { stream ->
                    Card(
                        modifier = Modifier
                            .width(170.dp)
                            .clickable {
                                titleInput = stream.title
                                urlInput = stream.url
                                subtitleInput = stream.subtitleUrl ?: ""
                                Toast.makeText(context, "Filled preset: ${stream.title}", Toast.LENGTH_SHORT).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2563EB).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = stream.category,
                                    color = Color(0xFF60A5FA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stream.title,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Click to Autofill",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form Fields Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_fields_container"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Stream Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Video Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_title_input"),
                        leadingIcon = { Icon(imageVector = Icons.Default.Title, contentDescription = null, tint = Color(0xFF6366F1)) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Stream/File URL (http/https/rtsp)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_url_input"),
                        leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = Color(0xFF6366F1)) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = subtitleInput,
                        onValueChange = { subtitleInput = it },
                        label = { Text("Subtitle Track URL (Optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_subtitle_input"),
                        leadingIcon = { Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFF6366F1)) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Stream Now" Large Action
                    Button(
                        onClick = {
                            if (urlInput.isBlank() || titleInput.isBlank()) {
                                Toast.makeText(context, "Please enter a title and a video link desc.", Toast.LENGTH_SHORT).show()
                            } else {
                                val video = VideoModel(
                                    id = "custom_stream_${System.currentTimeMillis()}",
                                    title = titleInput,
                                    urlOrPath = urlInput,
                                    subtitleUrlOrPath = subtitleInput.ifBlank { null },
                                    isStream = urlInput.contains(".m3u8")
                                )
                                viewModel.playVideo(video)
                                onNavigateToPlayer()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stream_now_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play immediately", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stream Now", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary split horizontal actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (urlInput.isBlank() || titleInput.isBlank()) {
                                    Toast.makeText(context, "Please fill Title and URL to save.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showPlaylistDialog = true
                                }
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("stream_add_playlist_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (urlInput.isBlank() || titleInput.isBlank()) {
                                    Toast.makeText(context, "Please fill Title and URL to download.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.startDownload(
                                        context = context,
                                        title = titleInput,
                                        videoUrl = urlInput,
                                        subtitleUrl = subtitleInput.ifBlank { null }
                                    )
                                    Toast.makeText(context, "Download started! Switched to Downloads.", Toast.LENGTH_SHORT).show()
                                    onNavigateToDownloads()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("stream_offline_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DownloadDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Offline", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Add Stream to Playlist Picker Dialog
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Select Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("You don't have any playlists yet. Please create one on the Playlists tab.")
                    } else {
                        Text("Save '${titleInput}' into:", modifier = Modifier.padding(bottom = 12.dp))
                        Box(modifier = Modifier.heightIn(max = 200.dp)) {
                            LazyColumn {
                                items(playlists) { playlist ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                viewModel.addVideoToPlaylist(
                                                    playlistId = playlist.id,
                                                    title = titleInput,
                                                    urlOrPath = urlInput,
                                                    subtitleUrl = subtitleInput.ifBlank { null }
                                                )
                                                Toast.makeText(context, "Added to playlist!", Toast.LENGTH_SHORT).show()
                                                showPlaylistDialog = false
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = null)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(playlist.title, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
