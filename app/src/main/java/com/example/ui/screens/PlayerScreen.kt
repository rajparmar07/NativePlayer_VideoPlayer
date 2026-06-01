package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: VideoPlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentVideo by viewModel.currentPlayingVideo.collectAsState()
    val hardwareAccel by viewModel.hardwareAccelerationEnabled.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()

    if (currentVideo == null) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val video = currentVideo!!

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isLocked by remember { mutableStateOf(false) }
    var showLockIconOnly by remember { mutableStateOf(false) }
    var lockInteractionTrigger by remember { mutableIntStateOf(0) }

    val resetLockTimeout = {
        lockInteractionTrigger++
    }

    // Handle back press to release player
    BackHandler {
        if (isLocked) {
            showLockIconOnly = true
            resetLockTimeout()
        } else {
            exoPlayer.release()
            viewModel.clearActivePlayback()
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onBack()
        }
    }

    // Configure MediaItem when current video changes
    LaunchedEffect(video, hardwareAccel) {
        val uri = Uri.parse(video.urlOrPath)
        val schema = uri.scheme ?: ""
        
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)

        // Add subtitle track if exists
        val subtitlePath = video.subtitleUrlOrPath
        if (!subtitlePath.isNullOrEmpty()) {
            val mimeType = if (subtitlePath.endsWith(".srt")) {
                MimeTypes.APPLICATION_SUBRIP
            } else {
                MimeTypes.TEXT_VTT
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitlePath))
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        // Live stream mime detection
        if (video.isStream || video.urlOrPath.contains(".m3u8")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Controller states
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferPos by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedAspectRatio by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var aspectNotificationText by remember { mutableStateOf<String?>(null) }
    var lastAspectNotificationText by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var activeSubtitles by remember { mutableStateOf(!video.subtitleUrlOrPath.isNullOrEmpty()) }
    var codecInfo by remember { mutableStateOf("Hardware (Auto)") }
    var videoResolution by remember { mutableStateOf("Detecting...") }
    val displayName = remember(video.urlOrPath, video.title) {
        val cleanUrl = video.urlOrPath.substringBefore('?')
        val lastSegment = cleanUrl.substringAfterLast('/')
        if (lastSegment.isNotBlank() && lastSegment.contains('.')) {
            lastSegment
        } else {
            video.title
        }
    }
    var userInteractionTrigger by remember { mutableIntStateOf(0) }

    val resetControlsTimeout = {
        userInteractionTrigger++
    }

    // Lock screen floating icon autohide timer
    LaunchedEffect(showLockIconOnly, lockInteractionTrigger) {
        if (showLockIconOnly) {
            delay(4000)
            showLockIconOnly = false
        }
    }

    // Aspect ratio notification autohide timer
    LaunchedEffect(aspectNotificationText) {
        if (aspectNotificationText != null) {
            lastAspectNotificationText = aspectNotificationText!!
            delay(1500)
            aspectNotificationText = null
        }
    }

    // Periodically update progress from ExoPlayer
    LaunchedEffect(isPlaying) {
        while (true) {
            currentPos = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            bufferPos = exoPlayer.bufferedPosition
            isPlaying = exoPlayer.isPlaying
            delay(250)
        }
    }

    // Control autohide logic (resets whenever showControls OR userInteractionTrigger changes)
    LaunchedEffect(showControls, userInteractionTrigger) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Manage volume
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Listeners for video events
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    viewModel.playNext()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    videoResolution = "${width}x${height}"
                    if (width > height) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Read format information for video track if available to show real codecs!
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.getTrackFormat(i)
                                codecInfo = "${format.sampleMimeType ?: "N/A"} (${format.width}x${format.height})"
                                videoResolution = "${format.width}x${format.height}"
                            }
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .testTag("player_screen_root")
    ) {
        // Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use fully custom Compose UI dashboard
                    setBackgroundColor(Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = selectedAspectRatio
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent tap-target layer that is active when controls are hidden OR when screen is locked
        if (!showControls || isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isLocked) {
                            showLockIconOnly = !showLockIconOnly
                            if (showLockIconOnly) {
                                resetLockTimeout()
                            }
                        } else {
                            showControls = true
                        }
                    }
            )
        }

        // IMMERSIVE SUBTITLE LAYER (Native fallback subtitles displayed beautifully inside PlayerView by default)
        // Compose overlay controls
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = false
                    }
            ) {
                // Top control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                exoPlayer.release()
                                viewModel.clearActivePlayback()
                                onBack()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = displayName,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (video.isStream) "Live Stream • $videoResolution" else "Offline Copy • $videoResolution",
                                color = androidx.compose.ui.graphics.Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Top Actions
                    Row {
                        // Subtitle toggle
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                activeSubtitles = !activeSubtitles
                                val trackSelection = if (activeSubtitles) {
                                    TrackSelectionParameters.Builder(context).build()
                                } else {
                                    TrackSelectionParameters.Builder(context)
                                        .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
                                        .build()
                                }
                                exoPlayer.trackSelectionParameters = trackSelection
                            }
                        ) {
                            Icon(
                                imageVector = if (activeSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Subtitles",
                                tint = if (activeSubtitles) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray
                            )
                        }

                        // Mute toggle
                        IconButton(onClick = {
                            resetControlsTimeout()
                            isMuted = !isMuted
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        // Speed controller
                        var showSpeedMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = {
                                resetControlsTimeout()
                                showSpeedMenu = !showSpeedMenu
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            resetControlsTimeout()
                                            playbackSpeed = speed
                                            exoPlayer.setPlaybackSpeed(speed)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Aspect ratio toggle
                        IconButton(onClick = {
                            resetControlsTimeout()
                            selectedAspectRatio = when (selectedAspectRatio) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                    aspectNotificationText = "Fill / Stretch"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                                }
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                                    aspectNotificationText = "Zoom"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                                else -> {
                                    aspectNotificationText = "Fit to Screen"
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        // Lock Controls button
                        IconButton(
                            onClick = {
                                isLocked = true
                                showControls = false
                                showLockIconOnly = true
                                resetLockTimeout()
                            },
                            modifier = Modifier.testTag("player_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock Controls",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }

                // Middle Buttons: Double Tap overlays / Rewind, Play-Pause, Forward
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                viewModel.playPrevious()
                            },
                            enabled = currentQueueIndex > 0,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Prev Video",
                                tint = if (currentQueueIndex > 0) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.DarkGray,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val current = exoPlayer.currentPosition
                                exoPlayer.seekTo((current - 10000).coerceAtLeast(0))
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                isPlaying = exoPlayer.isPlaying
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("play_pause_video_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val current = exoPlayer.currentPosition
                                val total = exoPlayer.duration
                                exoPlayer.seekTo((current + 10000).coerceAtMost(total))
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Fast Forward 10s",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                viewModel.playNext()
                            },
                            enabled = currentQueueIndex < playbackQueue.size - 1,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (currentQueueIndex < playbackQueue.size - 1) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.DarkGray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bottom bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Audio description and hardware mode indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hardwareAccel) "⚙ HW ACCELERATED (On)" else "⚙ SOFTWARE DECODING (Fallback)",
                            color = if (hardwareAccel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Yellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "Speed: ${playbackSpeed}x",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Seek Slider
                    val sliderPos = if (duration > 0) currentPos.toFloat() / duration else 0f
                    Slider(
                        value = sliderPos,
                        onValueChange = {
                            resetControlsTimeout()
                            val target = (it * duration).toLong()
                            exoPlayer.seekTo(target)
                            currentPos = target
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = androidx.compose.ui.graphics.Color.DarkGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_seek_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (video.isStream) "LIVE" else formatTime(duration),
                            color = if (video.isStream) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (video.isStream) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Top Action Bar when Locked (only displays Unlock button on the far right)
        AnimatedVisibility(
            visible = isLocked && showLockIconOnly,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        showControls = true
                        showLockIconOnly = false
                        resetControlsTimeout()
                    },
                    modifier = Modifier.testTag("player_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock Controls",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = aspectNotificationText != null,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 72.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lastAspectNotificationText,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
