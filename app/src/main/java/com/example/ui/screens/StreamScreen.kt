package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VideoModel
import com.example.viewmodel.VideoPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    viewModel: VideoPlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val context = LocalContext.current
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var subtitleInput by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("stream_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sleek explanation text
            Text(
                text = "Stream online media content directly by entering the network link. StreamCache supports high-quality HTTP, HTTPS, RTSP, and HLS (M3U8) live streams.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            // Input Group
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_fields_container"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Stream Title (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    CustomOutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = "e.g. My Live Stream",
                        leadingIcon = Icons.Default.Title,
                        testTag = "stream_title_input"
                    )
                }

                // URL Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Stream Link / URL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    CustomOutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = "https://example.com/stream.m3u8",
                        leadingIcon = Icons.Default.Link,
                        testTag = "stream_url_input"
                    )
                }

                // Subtitle Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Subtitle URL (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    CustomOutlinedTextField(
                        value = subtitleInput,
                        onValueChange = { subtitleInput = it },
                        placeholder = "https://example.com/subtitles.srt",
                        leadingIcon = Icons.Default.Subtitles,
                        testTag = "stream_subtitle_input"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Play Button
            Button(
                onClick = {
                    if (urlInput.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter a stream link")
                        }
                    } else {
                        val finalTitle = titleInput.ifBlank { "Network Stream" }
                        val video = VideoModel(
                            id = "custom_stream_${System.currentTimeMillis()}",
                            title = finalTitle,
                            urlOrPath = urlInput,
                            subtitleUrlOrPath = subtitleInput.ifBlank { null },
                            isStream = true
                        )
                        viewModel.playVideo(video)
                        onNavigateToPlayer()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("stream_now_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 1.dp,
                    pressedElevation = 2.dp,
                    focusedElevation = 1.dp,
                    hoveredElevation = 1.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Stream Now",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stream Now",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
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
    }
}

@Composable
private fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag(testTag),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
