@file:kotlin.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.view.OrientationEventListener
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Memory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.content.Intent
import com.example.viewmodel.VideoPlayerViewModel
import com.example.viewmodel.SubtitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*

// ─── Gesture control constants ───────────────────────────────────────────────
private const val SEEK_PIXELS_PER_STEP   = 60     // pixels of drag per seek step
private const val BRIGHTNESS_SENSITIVITY = 0.0015f // brightness change per pixel (reduced for comfort)
private const val VOLUME_PIXELS_PER_STEP = 1000    // pixels of drag per system volume step (increased for comfort)
private const val GESTURE_HUD_HIDE_DELAY = 800L   // ms before HUD fades after drag ends
// ─────────────────────────────────────────────────────────────────────────────

/** Gesture zones active during video playback. Locked in at drag-start, never changes mid-gesture. */
private enum class GestureType { BRIGHTNESS, VOLUME, SEEK }

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format,
    val isSelected: Boolean
)

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format,
    val isSelected: Boolean
)

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Failed to query filename", e)
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "subtitle.srt"
}

@kotlin.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
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
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val audioFocusEnabled by viewModel.audioFocusEnabled.collectAsState()
    val pauseOnHeadphonesDisconnectEnabled by viewModel.pauseOnHeadphonesDisconnectEnabled.collectAsState()
    val buttonSeekSeconds by viewModel.buttonSeekSeconds.collectAsState()
    var scale by remember { mutableFloatStateOf(1f) }

    // Fast Seek & 4K HEVC Override logic
    var is4kHevcVideo by remember { mutableStateOf(false) }
    val fastSeekGlobal by viewModel.fastSeekEnabled.collectAsState()
    val activeFastSeek = fastSeekGlobal || is4kHevcVideo
    var isSliderDragging by remember { mutableStateOf(false) }

    var externalSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showNoSubtitlesPrompt by remember { mutableStateOf(false) }
    var selectedSubtitleGroupIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSubtitleTrackIndex by remember { mutableStateOf<Int?>(null) }
    var selectedSubtitleLanguage by remember { mutableStateOf<String?>(null) }
    var selectedAudioGroupIndex by remember { mutableStateOf<Int?>(null) }
    var selectedAudioTrackIndex by remember { mutableStateOf<Int?>(null) }
    var selectedAudioLanguage by remember { mutableStateOf<String?>(null) }
    var isSubtitleDisabled by remember { mutableStateOf(true) }
    var activeSubtitles by remember { mutableStateOf(false) }
    var isSeekable by remember { mutableStateOf(true) }
    var decoderResetSeekingDisabled by remember { mutableStateOf(false) }
    var lastSeekTargetMs by remember { mutableLongStateOf(-1L) }
    var lastSeekTimeMs by remember { mutableLongStateOf(0L) }

    var playbackErrorMsg by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            delay(3000L)
            snackbarMessage = null
        }
    }

    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    var lastVideoUrl by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var decoderNotificationText by remember { mutableStateOf<String?>(null) }
    var lastDecoderNotificationText by remember { mutableStateOf("") }

    var showOrientationSuggestion by remember { mutableStateOf(false) }
    var suggestedTargetOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var orientationSuggestionTrigger by remember { mutableIntStateOf(0) }
    var lastPhysicalOrientation by remember { mutableStateOf(DeviceOrientation.UNKNOWN) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.e("PlayerScreen", "Failed to take persistable URI permission", e)
                }
                externalSubtitleUri = uri
            }
        }
    )

    val openFilePicker = {
        try {
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            snackbarMessage = "Error opening file picker"
        }
    }

    if (currentVideo == null) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val video = currentVideo!!

    // Initialize ExoPlayer (recreated when hardwareAccel changes to reload rendering pipeline)
    val exoPlayer = remember(hardwareAccel) {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val sanitizer = ChannelMaskSanitizerAudioProcessor()
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(sanitizer))
                    .build()
            }

            override fun buildTextRenderers(
                context: android.content.Context,
                output: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                val textRenderer = androidx.media3.exoplayer.text.TextRenderer(output, outputLooper)
                textRenderer.experimentalSetLegacyDecodingEnabled(true)
                out.add(textRenderer)
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
        val mediaSourceFactory = CustomMediaSourceFactory(context, dataSourceFactory)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
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
            viewModel.clearActivePlayback()
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onBack()
        }
    }

    // Reset or restore track selections on video change
    LaunchedEffect(video) {
        if (lastVideoUrl != video.urlOrPath) {
            lastVideoUrl = video.urlOrPath
            externalSubtitleUri = null
            isSeekable = true
            decoderResetSeekingDisabled = false
            lastSeekTargetMs = -1L
            lastSeekTimeMs = 0L

            val resumeEnabled = viewModel.resumeFromLastLeftEnabled.value
            val savedState = if (resumeEnabled) viewModel.getVideoPlaybackState(video.urlOrPath) else null
            if (savedState != null) {
                selectedAudioGroupIndex = savedState.audioGroupIndex
                selectedAudioTrackIndex = savedState.audioTrackIndex
                selectedAudioLanguage = savedState.audioLanguage
                selectedSubtitleGroupIndex = savedState.subtitleGroupIndex
                selectedSubtitleTrackIndex = savedState.subtitleTrackIndex
                selectedSubtitleLanguage = savedState.subtitleLanguage
                isSubtitleDisabled = savedState.isSubtitleDisabled
                activeSubtitles = !savedState.isSubtitleDisabled
            } else {
                selectedSubtitleGroupIndex = null
                selectedSubtitleTrackIndex = null
                selectedSubtitleLanguage = null
                selectedAudioGroupIndex = null
                selectedAudioTrackIndex = null
                selectedAudioLanguage = null
                isSubtitleDisabled = true
                activeSubtitles = false
            }
        }
    }

    // Configure MediaItem when current video changes or external subtitle is added
    LaunchedEffect(video, hardwareAccel, externalSubtitleUri) {
        scale = 1f
        is4kHevcVideo = false
        // If it's a completely new video, start from progressMap. Otherwise preserve current position.
        val isNewVideo = lastVideoUrl != video.urlOrPath
        val currentPosBeforeReload = if (isNewVideo) 0L else exoPlayer.currentPosition
        if (isNewVideo) {
            lastVideoUrl = video.urlOrPath
        }

        val uri = if (video.isLocal) {
            val idLong = video.id.toLongOrNull()
            if (idLong != null) {
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    idLong
                )
            } else {
                Uri.parse(video.urlOrPath)
            }
        } else {
            Uri.parse(video.urlOrPath)
        }
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)

        val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

        // Add database subtitle if exists
        val dbSubtitlePath = video.subtitleUrlOrPath
        if (!dbSubtitlePath.isNullOrEmpty()) {
            val mimeType = if (dbSubtitlePath.endsWith(".srt")) {
                MimeTypes.APPLICATION_SUBRIP
            } else {
                MimeTypes.TEXT_VTT
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(dbSubtitlePath))
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            subtitleConfigs.add(subtitleConfig)
        }

        // Add side-loaded external subtitle if exists
        externalSubtitleUri?.let { extUri ->
            val extUriStr = extUri.toString()
            val mimeType = if (extUriStr.endsWith(".srt") || getFileName(context, extUri).endsWith(".srt")) {
                MimeTypes.APPLICATION_SUBRIP
            } else {
                MimeTypes.TEXT_VTT
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(extUri)
                .setMimeType(mimeType)
                .setLanguage("en (External)")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            subtitleConfigs.add(subtitleConfig)
        }

        if (subtitleConfigs.isNotEmpty()) {
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        // Live stream mime detection
        if (video.isStream || video.urlOrPath.contains(".m3u8")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        
        if (currentPosBeforeReload > 0L) {
            exoPlayer.seekTo(currentPosBeforeReload)
        } else {
            val resumeEnabled = viewModel.resumeFromLastLeftEnabled.value
            if (resumeEnabled) {
                val progressMap = viewModel.videoProgressMap.value
                val savedProgress = progressMap[video.urlOrPath]?.first ?: 0L
                if (savedProgress > 0L) {
                    exoPlayer.seekTo(savedProgress)
                }
            }
        }
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
    var codecInfo by remember { mutableStateOf("Hardware (Auto)") }
    var videoResolution by remember { mutableStateOf("Detecting...") }
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val waveAmplitudeTarget = if (isPlaying) 3.dp else 0.dp
    val waveAmplitude by animateDpAsState(
        targetValue = waveAmplitudeTarget,
        animationSpec = tween(500),
        label = "wave_amplitude"
    )
    val displayName = remember(video.urlOrPath, video.title, video.isStream) {
        if (video.isStream && video.title.isNotBlank() && video.title != "Network Stream") {
            video.title
        } else {
            val cleanUrl = video.urlOrPath.substringBefore('?')
            val lastSegment = cleanUrl.substringAfterLast('/')
            if (lastSegment.isNotBlank() && lastSegment.contains('.') && !video.isStream) {
                lastSegment
            } else {
                video.title
            }
        }
    }
    var userInteractionTrigger by remember { mutableIntStateOf(0) }

    // ─── Gesture control state ────────────────────────────────────────────────
    val seekStepMs by viewModel.gestureSeekMs.collectAsState()
    val controllerTimeout by viewModel.playerControllerTimeout.collectAsState()
    val seekGestureEnabled by viewModel.seekGestureEnabled.collectAsState()
    val volumeGestureEnabled by viewModel.volumeGestureEnabled.collectAsState()
    val brightnessGestureEnabled by viewModel.brightnessGestureEnabled.collectAsState()
    val seekSwipePixels by viewModel.seekSwipePixels.collectAsState()
    val volumeSwipePixels by viewModel.volumeSwipePixels.collectAsState()
    val brightnessSensitivitySetting by viewModel.brightnessSensitivity.collectAsState()
    val zoomGestureEnabled by viewModel.zoomGestureEnabled.collectAsState()
    val zoomSensitivity by viewModel.zoomSensitivity.collectAsState()
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Accumulated drag deltas — reset on each new drag gesture
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    // Which horizontal zone the drag started in (true = left half = brightness)
    var gestureStartZoneLeft by remember { mutableStateOf(false) }
    // Type locked in on first significant movement within a single drag
    var activeGestureType by remember { mutableStateOf<GestureType?>(null) }
    var isGestureSeeking by remember { mutableStateOf(false) }
    // Seek delta accumulates during drag; committed to ExoPlayer only on drag-end
    var gestureSeekOffset by remember { mutableLongStateOf(0L) }
    // Brightness and volume mirror current levels for HUD display
    var gestureBrightness by remember {
        mutableFloatStateOf(
            (context as? Activity)?.window?.attributes?.screenBrightness
                ?.takeIf { it >= 0f } ?: 0.5f
        )
    }
    var gestureVolume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.coerceAtLeast(1)
        )
    }
    var showGestureHud by remember { mutableStateOf(false) }
    var gestureHudHideTrigger by remember { mutableIntStateOf(0) }
    // dragStartPos: position in the video when the current seek drag started
    // Used as a stable anchor so every seek step is relative to the drag origin.
    var dragStartPos by remember { mutableLongStateOf(0L) }
    // lastSeekSteps: tracks the last committed step count to fire seeks only on step changes
    var lastSeekSteps by remember { mutableLongStateOf(0L) }
    // isPendingPostSwipe: true after swipe drag ends until final seek and buffering are complete
    var isPendingPostSwipe by remember { mutableStateOf(false) }
    // wasPlayingBeforeSwipe: remembers if video was playing before user initiated swipe seek gesture
    var wasPlayingBeforeSwipe by remember { mutableStateOf(false) }
    // ─────────────────────────────────────────────────────────────────────────

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

    // Decoder notification autohide timer
    LaunchedEffect(decoderNotificationText) {
        if (decoderNotificationText != null) {
            lastDecoderNotificationText = decoderNotificationText!!
            delay(1500)
            decoderNotificationText = null
        }
    }

    // Periodically update progress from ExoPlayer
    LaunchedEffect(isPlaying, exoPlayer) {
        while (true) {
            if (!isGestureSeeking && !isSliderDragging) {
                val current = exoPlayer.currentPosition
                currentPos = current
                duration = exoPlayer.duration.coerceAtLeast(0L)
                bufferPos = exoPlayer.bufferedPosition
                isPlaying = exoPlayer.isPlaying
                viewModel.setVideoPlaying(isPlaying)

                val nativeSeekable = exoPlayer.isCurrentMediaItemSeekable
                val now = System.currentTimeMillis()
                val seekResetDetected = isSeekable &&
                        lastSeekTargetMs > 5000L &&
                        (now - lastSeekTimeMs) < 2500L &&
                        current < 2000L &&
                        (lastSeekTargetMs - current) > 4000L

                if (seekResetDetected) {
                    if (!decoderResetSeekingDisabled) {
                        decoderResetSeekingDisabled = true
                        isSeekable = false
                        android.widget.Toast.makeText(
                            context,
                            "Seeking disabled: video decoder reset detected",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (!decoderResetSeekingDisabled) {
                    isSeekable = nativeSeekable
                }
            }
            delay(250)
        }
    }

    // Apply seek parameters dynamically
    LaunchedEffect(activeFastSeek, exoPlayer) {
        exoPlayer.setSeekParameters(
            if (activeFastSeek) SeekParameters.CLOSEST_SYNC
            else SeekParameters.DEFAULT
        )
    }

    // Apply audio focus & noisy disconnect handling dynamically
    LaunchedEffect(audioFocusEnabled, pauseOnHeadphonesDisconnectEnabled, exoPlayer) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, audioFocusEnabled)
        exoPlayer.setHandleAudioBecomingNoisy(pauseOnHeadphonesDisconnectEnabled)
    }

    val saveCurrentProgress = {
        val currentPosition = exoPlayer.currentPosition
        val totalDuration = exoPlayer.duration
        if (totalDuration > 0) {
            viewModel.saveVideoProgress(
                urlOrPath = video.urlOrPath,
                progressMs = currentPosition,
                durationMs = totalDuration,
                audioGroupIndex = selectedAudioGroupIndex,
                audioTrackIndex = selectedAudioTrackIndex,
                audioLanguage = selectedAudioLanguage,
                subtitleGroupIndex = selectedSubtitleGroupIndex,
                subtitleTrackIndex = selectedSubtitleTrackIndex,
                subtitleLanguage = selectedSubtitleLanguage,
                isSubtitleDisabled = isSubtitleDisabled
            )
        }
    }

    // Periodically save progress & track preferences to preferences (every 5 seconds)
    LaunchedEffect(video, isPlaying, exoPlayer, selectedAudioGroupIndex, selectedAudioTrackIndex, selectedSubtitleGroupIndex, selectedSubtitleTrackIndex, isSubtitleDisabled) {
        if (isPlaying) {
            while (true) {
                delay(5000)
                saveCurrentProgress()
            }
        }
    }

    // Save final progress & track preferences on exit or video swap
    DisposableEffect(video, exoPlayer) {
        onDispose {
            saveCurrentProgress()
        }
    }

    // Control autohide logic (resets whenever showControls, userInteractionTrigger OR controllerTimeout changes)
    LaunchedEffect(showControls, userInteractionTrigger, controllerTimeout) {
        if (showControls) {
            delay(controllerTimeout * 1000L)
            showControls = false
        }
    }

    // Gesture HUD auto-hide: triggered after each drag ends
    LaunchedEffect(gestureHudHideTrigger) {
        if (showGestureHud) {
            delay(GESTURE_HUD_HIDE_DELAY)
            showGestureHud = false
            activeGestureType = null
            // Wait for the fadeOut exit animation (300ms) to fully complete before
            // resetting seek state. This ensures:
            //  1. Seek text never flickers to "+0s" while HUD is still visible
            //  2. Controls don't reappear until after the HUD has fully faded out
            delay(300L)
            gestureSeekOffset = 0L
            isGestureSeeking = false
        }
    }

    // Auto-dismiss post-swipe pending interaction block when ExoPlayer completes buffering or via timeout
    LaunchedEffect(isPendingPostSwipe) {
        if (isPendingPostSwipe) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_ENDED || exoPlayer.playbackState == Player.STATE_IDLE) {
                delay(150L)
                isPendingPostSwipe = false
            } else {
                val startTime = System.currentTimeMillis()
                while (isPendingPostSwipe && exoPlayer.playbackState == Player.STATE_BUFFERING && (System.currentTimeMillis() - startTime < 2500L)) {
                    delay(50L)
                }
                isPendingPostSwipe = false
            }
            if (wasPlayingBeforeSwipe) {
                exoPlayer.play()
                wasPlayingBeforeSwipe = false
            }
        }
    }

    // Manage volume
    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer) {
        viewModel.playbackCommand.collect { command ->
            when (command) {
                VideoPlayerViewModel.PlaybackCommand.PLAY -> exoPlayer.play()
                VideoPlayerViewModel.PlaybackCommand.PAUSE -> exoPlayer.pause()
            }
        }
    }

    // Device orientation sensor tracking (Transition-aware to avoid shake-triggered repetition)
    DisposableEffect(exoPlayer) {
        val activity = context as? Activity
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val isPhysicalPortrait = orientation in 330..359 || orientation in 0..30
                val isPhysicalLandscape = orientation in 60..120 || orientation in 240..300

                val currentPhysical = when {
                    isPhysicalPortrait -> DeviceOrientation.PORTRAIT
                    isPhysicalLandscape -> DeviceOrientation.LANDSCAPE
                    else -> DeviceOrientation.UNKNOWN
                }

                val isScreenPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val isScreenLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                if (currentPhysical != DeviceOrientation.UNKNOWN && currentPhysical != lastPhysicalOrientation) {
                    lastPhysicalOrientation = currentPhysical

                    // If current screen is landscape but device is held vertically (suggest portrait)
                    if (currentPhysical == DeviceOrientation.PORTRAIT && isScreenLandscape) {
                        suggestedTargetOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        showOrientationSuggestion = true
                        orientationSuggestionTrigger++
                    }
                    // If current screen is portrait but device is held horizontally (suggest landscape)
                    else if (currentPhysical == DeviceOrientation.LANDSCAPE && isScreenPortrait) {
                        suggestedTargetOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        showOrientationSuggestion = true
                        orientationSuggestionTrigger++
                    }
                } else if (currentPhysical != DeviceOrientation.UNKNOWN) {
                    // If the user rotates the device back to match current screen, hide recommendation immediately
                    val matchesLandscape = currentPhysical == DeviceOrientation.LANDSCAPE && isScreenLandscape
                    val matchesPortrait = currentPhysical == DeviceOrientation.PORTRAIT && isScreenPortrait

                    if ((matchesLandscape || matchesPortrait) && showOrientationSuggestion) {
                        showOrientationSuggestion = false
                    }
                }
            }
        }

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        onDispose {
            orientationListener.disable()
        }
    }

    // Auto-dismiss orientation suggestion after 5 seconds
    LaunchedEffect(orientationSuggestionTrigger) {
        if (showOrientationSuggestion) {
            delay(5000L)
            showOrientationSuggestion = false
        }
    }

    // Sync system bars visibility with player controls visibility
    LaunchedEffect(showControls) {
        val activity = context as? Activity
        activity?.window?.let { win ->
            val windowInsetsController = WindowCompat.getInsetsController(win, win.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showControls) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Screen Lifecycle (Brightness, Orientation, & Immersive System Bars resets/coloring)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        
        // Save original system bar styles and colors
        val originalStatusBarColor = activity?.window?.statusBarColor
        val originalNavigationBarColor = activity?.window?.navigationBarColor
        val originalLightStatusBars = activity?.window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightStatusBars
        }
        val originalLightNavigationBars = activity?.window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightNavigationBars
        }

        // Apply dark semi-transparent backgrounds and white icons for system bars
        activity?.window?.let { win ->
            win.statusBarColor = android.graphics.Color.parseColor("#80000000")
            win.navigationBarColor = android.graphics.Color.parseColor("#80000000")
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                // Restore status bar and navigation bar
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                
                // Restore original appearance styles
                if (originalLightStatusBars != null) {
                    controller.isAppearanceLightStatusBars = originalLightStatusBars
                }
                if (originalLightNavigationBars != null) {
                    controller.isAppearanceLightNavigationBars = originalLightNavigationBars
                }

                // Restore original system bar colors
                if (originalStatusBarColor != null) win.statusBarColor = originalStatusBarColor
                if (originalNavigationBarColor != null) win.navigationBarColor = originalNavigationBarColor

                // Reset screen brightness
                val attrs = win.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = attrs
            }
        }
    }

    // Player Instance Lifecycle (registers listeners and releases player when recreated or exited)
    DisposableEffect(exoPlayer) {
        val activity = context as? Activity
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isLoading = state == Player.STATE_BUFFERING
                if (isPendingPostSwipe && state != Player.STATE_BUFFERING) {
                    isPendingPostSwipe = false
                }
                if (state == Player.STATE_ENDED) {
                    viewModel.playNext()
                }
            }

            override fun onIsPlayingChanged(isPlayingNew: Boolean) {
                isPlaying = isPlayingNew
                viewModel.setVideoPlaying(isPlayingNew)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    videoResolution = "${width}x${height}"
                    viewModel.setVideoDimensions(width, height)
                    if (width > height) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Read format information for video track if available to show real codecs!
                var hevc4k = false
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.getTrackFormat(i)
                                codecInfo = "${format.sampleMimeType ?: "N/A"} (${format.width}x${format.height})"
                                videoResolution = "${format.width}x${format.height}"
                                viewModel.setVideoDimensions(format.width, format.height)

                                val mime = format.sampleMimeType
                                val width = format.width
                                val height = format.height
                                val isHevc = mime == MimeTypes.VIDEO_H265 || (mime != null && mime.contains("hevc", ignoreCase = true))
                                val isHighRes = width >= 3840 || height >= 2160
                                if (isHevc && isHighRes) {
                                    hevc4k = true
                                }
                            }
                        }
                    }
                }
                is4kHevcVideo = hevc4k

                // Query subtitle tracks (no longer filtering by isTrackSupported to avoid platform capabilities reporting bugs)
                var textSelected = false
                val list = mutableListOf<SubtitleTrackInfo>()
                for (groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[groupIndex]
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val isSelected = group.isTrackSelected(trackIndex)
                            if (isSelected) {
                                textSelected = true
                            }
                            list.add(SubtitleTrackInfo(groupIndex, trackIndex, format, isSelected))
                        }
                    }
                }
                subtitleTracks = list
                activeSubtitles = textSelected

                // Query audio tracks
                val audioList = mutableListOf<AudioTrackInfo>()
                for (groupIndex in 0 until tracks.groups.size) {
                    val group = tracks.groups[groupIndex]
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val isSelected = group.isTrackSelected(trackIndex)
                            audioList.add(AudioTrackInfo(groupIndex, trackIndex, format, isSelected))
                        }
                    }
                }
                audioTracks = audioList

                // Restore track selections if they exist
                var parametersBuilder = exoPlayer.trackSelectionParameters.buildUpon()
                var updated = false

                if (isSubtitleDisabled) {
                    if (!exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                        parametersBuilder = parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        updated = true
                    }
                } else {
                    var targetGroupIdx = selectedSubtitleGroupIndex
                    var targetTrackIdx = selectedSubtitleTrackIndex

                    if ((targetGroupIdx == null || targetGroupIdx >= tracks.groups.size) && selectedSubtitleLanguage != null) {
                        for (gIdx in 0 until tracks.groups.size) {
                            val group = tracks.groups[gIdx]
                            if (group.type == C.TRACK_TYPE_TEXT) {
                                for (tIdx in 0 until group.length) {
                                    val format = group.getTrackFormat(tIdx)
                                    if (format.language == selectedSubtitleLanguage) {
                                        targetGroupIdx = gIdx
                                        targetTrackIdx = tIdx
                                        selectedSubtitleGroupIndex = gIdx
                                        selectedSubtitleTrackIndex = tIdx
                                        break
                                    }
                                }
                            }
                        }
                    }

                    if (targetGroupIdx != null && targetTrackIdx != null && targetGroupIdx < tracks.groups.size) {
                        val group = tracks.groups[targetGroupIdx]
                        if (group.type == C.TRACK_TYPE_TEXT && targetTrackIdx < group.length) {
                            val trackGroup = group.mediaTrackGroup
                            val hasOverride = exoPlayer.trackSelectionParameters.overrides.containsKey(trackGroup)
                            if (!hasOverride || exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                                parametersBuilder = parametersBuilder
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .addOverride(TrackSelectionOverride(trackGroup, targetTrackIdx))
                                updated = true
                            }
                        }
                    }
                }

                var targetAudioGroupIdx = selectedAudioGroupIndex
                var targetAudioTrackIdx = selectedAudioTrackIndex

                if ((targetAudioGroupIdx == null || targetAudioGroupIdx >= tracks.groups.size) && selectedAudioLanguage != null) {
                    for (gIdx in 0 until tracks.groups.size) {
                        val group = tracks.groups[gIdx]
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (tIdx in 0 until group.length) {
                                val format = group.getTrackFormat(tIdx)
                                if (format.language == selectedAudioLanguage) {
                                    targetAudioGroupIdx = gIdx
                                    targetAudioTrackIdx = tIdx
                                    selectedAudioGroupIndex = gIdx
                                    selectedAudioTrackIndex = tIdx
                                    break
                                }
                            }
                        }
                    }
                }

                if (targetAudioGroupIdx != null && targetAudioTrackIdx != null && targetAudioGroupIdx < tracks.groups.size) {
                    val group = tracks.groups[targetAudioGroupIdx]
                    if (group.type == C.TRACK_TYPE_AUDIO && targetAudioTrackIdx < group.length) {
                        val trackGroup = group.mediaTrackGroup
                        val hasOverride = exoPlayer.trackSelectionParameters.overrides.containsKey(trackGroup)
                        if (!hasOverride) {
                            parametersBuilder = parametersBuilder
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(TrackSelectionOverride(trackGroup, targetAudioTrackIdx))
                            updated = true
                        }
                    }
                }

                if (updated) {
                    exoPlayer.trackSelectionParameters = parametersBuilder.build()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val errorMessage = when {
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                        "Codec/Decoder initialization failed. Your device might not support this video profile or audio format (like 4K HEVC 10-bit or Dolby E-AC-3)."
                    }
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                        "Decoding failed. The file is corrupted or uses an unsupported codec format."
                    }
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                        "File read error. Please check permissions or file integrity."
                    }
                    cause != null && cause.message?.contains("EAC3", ignoreCase = true) == true ||
                    cause != null && cause.message?.contains("ac-3", ignoreCase = true) == true -> {
                        "Audio format (Dolby Digital Plus EAC3) is not supported by your device."
                    }
                    cause != null && cause.message?.contains("hevc", ignoreCase = true) == true ||
                    cause != null && cause.message?.contains("h265", ignoreCase = true) == true -> {
                        "Video format (HEVC H.265 10-bit) is not supported by your device."
                    }
                    else -> {
                        "Playback error: ${error.localizedMessage ?: "Unknown error"}"
                    }
                }
                playbackErrorMsg = errorMessage
                isLoading = false
                Log.e("PlayerScreen", "Player error occurred: ${error.errorCodeName} (${error.errorCode})", error)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            viewModel.setVideoPlaying(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .testTag("player_screen_root")
            // ── Pinch to Zoom gesture detector ──────────────────────────────────
            .pointerInput(isLocked, isInPipMode, zoomGestureEnabled, zoomSensitivity) {
                if (isInPipMode || isLocked || !zoomGestureEnabled) return@pointerInput
                awaitPointerEventScope {
                    var lastDistance = -1f
                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.size >= 2) {
                            val p1 = activePointers[0].position
                            val p2 = activePointers[1].position
                            val dx = p1.x - p2.x
                            val dy = p1.y - p2.y
                            val currentDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                            
                            // Consume the touch events so they don't trigger drag actions
                            event.changes.forEach { it.consume() }

                            if (lastDistance > 0f && currentDistance > 0f) {
                                val ratio = currentDistance / lastDistance
                                val delta = 1f + (ratio - 1f) * zoomSensitivity
                                scale = (scale * delta).coerceIn(1f, 4f)
                            }
                            lastDistance = currentDistance
                        } else {
                            lastDistance = -1f
                        }
                        if (event.changes.none { it.pressed }) {
                            lastDistance = -1f
                        }
                    }
                }
            }
            // ── Gesture detection (works in both portrait & landscape) ──────────
            // Re-registered when gesture settings, seeking readiness, or lock states change.
            .pointerInput(isLocked, isInPipMode, isPendingPostSwipe, seekGestureEnabled, volumeGestureEnabled, brightnessGestureEnabled, isSeekable, seekSwipePixels, seekStepMs, volumeSwipePixels, brightnessSensitivitySetting, exoPlayer) {
                if (isInPipMode || isPendingPostSwipe) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLocked || isPendingPostSwipe) return@detectDragGestures
                        // Reset accumulators and classify zone from drag origin
                        dragAccumX = 0f
                        dragAccumY = 0f
                        gestureStartZoneLeft = offset.x < size.width / 2f
                        activeGestureType = null
                        isGestureSeeking = false
                        showGestureHud = false
                        // Capture stable seek anchor and reset step tracker
                        dragStartPos = currentPos
                        lastSeekSteps = 0L
                    },
                    onDrag = { change, dragAmount ->
                        if (isLocked || isPendingPostSwipe) return@detectDragGestures
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y

                        // Lock the gesture type on the first significant movement
                        if (activeGestureType == null) {
                            when {
                                // Horizontal dominant (>10px threshold) → seek
                                abs(dragAccumX) > 10f && abs(dragAccumX) > abs(dragAccumY) * 1.2f && seekGestureEnabled && isSeekable -> {
                                    activeGestureType = GestureType.SEEK
                                    isGestureSeeking = true
                                    wasPlayingBeforeSwipe = exoPlayer.isPlaying
                                    if (wasPlayingBeforeSwipe) {
                                        exoPlayer.pause()
                                    }
                                    // CLOSEST_SYNC snaps to the nearest keyframe — far faster
                                    // than exact-frame seeks, which is what makes scrubbing smooth
                                    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                    showGestureHud = true
                                }
                                // Vertical dominant (>10px threshold) → brightness or volume
                                abs(dragAccumY) > 10f && abs(dragAccumY) > abs(dragAccumX) * 1.2f -> {
                                    if (gestureStartZoneLeft && brightnessGestureEnabled) {
                                        activeGestureType = GestureType.BRIGHTNESS
                                        showGestureHud = true
                                    } else if (!gestureStartZoneLeft && volumeGestureEnabled) {
                                        activeGestureType = GestureType.VOLUME
                                        showGestureHud = true
                                    }
                                }
                            }
                        }

                        when (activeGestureType) {
                            GestureType.SEEK -> {
                                // Steps accumulated relative to drag start anchor
                                val steps = (dragAccumX / seekSwipePixels).toLong()
                                gestureSeekOffset = steps * seekStepMs
                                // Live seek: fire exoPlayer.seekTo on every tiny step chunk crossing
                                val target = (dragStartPos + gestureSeekOffset).coerceIn(0L, duration)
                                currentPos = target
                                if (steps != lastSeekSteps) {
                                    lastSeekSteps = steps
                                    lastSeekTargetMs = target
                                    lastSeekTimeMs = System.currentTimeMillis()
                                    exoPlayer.seekTo(target)
                                }
                            }
                            GestureType.BRIGHTNESS -> {
                                // Swipe up = positive deltaY is downward in Compose → invert
                                gestureBrightness = (gestureBrightness - dragAmount.y * brightnessSensitivitySetting)
                                    .coerceIn(0.01f, 1.0f)
                                val activity = context as? Activity
                                activity?.window?.let { win ->
                                    val attrs = win.attributes
                                    attrs.screenBrightness = gestureBrightness
                                    win.attributes = attrs
                                }
                            }
                            GestureType.VOLUME -> {
                                val step = dragAmount.y / volumeSwipePixels
                                gestureVolume = (gestureVolume - step).coerceIn(0f, 1f)
                                val newVol = (gestureVolume * maxVolume).toInt()
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVol,
                                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                                )
                                // Clear in-app mute if user deliberately raises volume
                                if (gestureVolume > 0f && isMuted) isMuted = false
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        if (isGestureSeeking) {
                            val target = (dragStartPos + gestureSeekOffset).coerceIn(0L, duration)
                            android.util.Log.d("PlayerScreen", "Gesture drag end: target=$target, activeFastSeek=$activeFastSeek, seekable=${exoPlayer.isCurrentMediaItemSeekable}")
                            lastSeekTargetMs = target
                            lastSeekTimeMs = System.currentTimeMillis()
                            exoPlayer.seekTo(target)
                            currentPos = target
                            exoPlayer.setSeekParameters(
                                if (activeFastSeek) SeekParameters.CLOSEST_SYNC
                                else SeekParameters.DEFAULT
                            )
                            isPendingPostSwipe = true
                            if (wasPlayingBeforeSwipe) {
                                exoPlayer.play()
                                wasPlayingBeforeSwipe = false
                            }
                        }
                        // Brightness: intentionally NOT reset here — the gesture-set level
                        // stays for the full player session (matching VLC / MX Player behavior).
                        // BRIGHTNESS_OVERRIDE_NONE is restored in onDispose when the player exits.
                        gestureHudHideTrigger++
                    },
                    onDragCancel = {
                        if (isGestureSeeking) {
                            val target = (dragStartPos + gestureSeekOffset).coerceIn(0L, duration)
                            android.util.Log.d("PlayerScreen", "Gesture drag cancel: target=$target, activeFastSeek=$activeFastSeek, seekable=${exoPlayer.isCurrentMediaItemSeekable}")
                            lastSeekTargetMs = target
                            lastSeekTimeMs = System.currentTimeMillis()
                            exoPlayer.seekTo(target)
                            currentPos = target
                            exoPlayer.setSeekParameters(
                                if (activeFastSeek) SeekParameters.CLOSEST_SYNC
                                else SeekParameters.DEFAULT
                            )
                            isPendingPostSwipe = true
                            if (wasPlayingBeforeSwipe) {
                                exoPlayer.play()
                                wasPlayingBeforeSwipe = false
                            }
                        }
                        gestureHudHideTrigger++
                    }
                )
            }
            // ─────────────────────────────────────────────────────────────────
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
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = selectedAspectRatio
                val style = when (subtitleStyle) {
                    SubtitleStyle.Default -> null
                    SubtitleStyle.ClassicWhite -> CaptionStyleCompat(
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.BLACK,
                        null
                    )
                    SubtitleStyle.WarmYellow -> CaptionStyleCompat(
                        Color.YELLOW,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.BLACK,
                        null
                    )
                    SubtitleStyle.CyanOutline -> CaptionStyleCompat(
                        Color.CYAN,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.BLACK,
                        null
                    )
                    SubtitleStyle.WhiteOnBlackBox -> CaptionStyleCompat(
                        Color.WHITE,
                        Color.argb(128, 0, 0, 0),
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        Color.TRANSPARENT,
                        null
                    )
                    SubtitleStyle.YellowOnBlackBox -> CaptionStyleCompat(
                        Color.YELLOW,
                        Color.argb(128, 0, 0, 0),
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        Color.TRANSPARENT,
                        null
                    )
                }
                if (style == null) {
                    view.subtitleView?.setUserDefaultStyle()
                } else {
                    view.subtitleView?.setStyle(style)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
        )

        // Transparent tap-target layer that is active when controls are hidden OR when screen is locked
        if ((!showControls || isLocked) && !isInPipMode) {
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
            visible = showControls && !isLocked && !isGestureSeeking && !isInPipMode,
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Top Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decoder Selector (Hardware vs Software)
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                showDecoderDialog = true
                            }
                        ) {
                            val tintColor = if (!hardwareAccel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SW",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tintColor,
                                    lineHeight = 17.sp
                                )
                                if (hardwareAccel) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(14.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        drawLine(
                                            color = tintColor,
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }

                        // Audio Track Selector
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val tracks = exoPlayer.currentTracks
                                val audioList = mutableListOf<AudioTrackInfo>()
                                for (groupIndex in 0 until tracks.groups.size) {
                                    val group = tracks.groups[groupIndex]
                                    if (group.type == C.TRACK_TYPE_AUDIO) {
                                        for (trackIndex in 0 until group.length) {
                                            val format = group.getTrackFormat(trackIndex)
                                            val isSelected = group.isTrackSelected(trackIndex)
                                            audioList.add(AudioTrackInfo(groupIndex, trackIndex, format, isSelected))
                                        }
                                    }
                                }
                                audioTracks = audioList
                                showAudioTrackDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Audio Tracks",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        // Subtitle toggle
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val tracks = exoPlayer.currentTracks
                                val list = mutableListOf<SubtitleTrackInfo>()
                                for (groupIndex in 0 until tracks.groups.size) {
                                    val group = tracks.groups[groupIndex]
                                    if (group.type == C.TRACK_TYPE_TEXT) {
                                        for (trackIndex in 0 until group.length) {
                                            val format = group.getTrackFormat(trackIndex)
                                            val isSelected = group.isTrackSelected(trackIndex)
                                            list.add(SubtitleTrackInfo(groupIndex, trackIndex, format, isSelected))
                                        }
                                    }
                                }
                                subtitleTracks = list
                                if (list.isEmpty()) {
                                    showNoSubtitlesPrompt = true
                                } else {
                                    showSubtitleDialog = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (activeSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Subtitles",
                                tint = if (activeSubtitles) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray
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
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sideControlBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        val sideBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        val sideIconTint = MaterialTheme.colorScheme.onSurface

                        // 1. Prev Video Button
                        val isPrevEnabled = currentQueueIndex > 0
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                viewModel.playPrevious()
                            },
                            enabled = isPrevEnabled,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isPrevEnabled) sideControlBg else sideControlBg.copy(alpha = 0.3f))
                                .border(1.dp, if (isPrevEnabled) sideBorderColor else sideBorderColor.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Prev Video",
                                tint = if (isPrevEnabled) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 2. Rewind Button (Dynamic buttonSeekSeconds)
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val current = exoPlayer.currentPosition
                                val seekStepMs = buttonSeekSeconds * 1000L
                                val target = (current - seekStepMs).coerceAtLeast(0)
                                lastSeekTargetMs = target
                                lastSeekTimeMs = System.currentTimeMillis()
                                exoPlayer.seekTo(target)
                            },
                            enabled = isSeekable,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSeekable) sideControlBg else sideControlBg.copy(alpha = 0.3f))
                                .border(1.dp, if (isSeekable) sideBorderColor else sideBorderColor.copy(alpha = 0.15f), CircleShape)
                        ) {
                            val rewindIcon = when (buttonSeekSeconds) {
                                5 -> Icons.Default.Replay5
                                30 -> Icons.Default.Replay30
                                else -> Icons.Default.Replay10
                            }
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = rewindIcon,
                                    contentDescription = "Rewind ${buttonSeekSeconds}s",
                                    tint = if (isSeekable) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                                if (buttonSeekSeconds != 5 && buttonSeekSeconds != 10 && buttonSeekSeconds != 30) {
                                    Text(
                                        text = "$buttonSeekSeconds",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSeekable) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // 3. Main Play / Pause Button (Compact 64dp with Icon Morph Animation)
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
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f), CircleShape)
                                .testTag("play_pause_video_button")
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                     scaleIn(initialScale = 0.4f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                            scaleOut(targetScale = 0.4f, animationSpec = tween(180, easing = FastOutSlowInEasing))
                                        )
                                },
                                label = "play_pause_icon_morph"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // 4. Fast Forward Button (Dynamic buttonSeekSeconds)
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                val current = exoPlayer.currentPosition
                                val total = exoPlayer.duration
                                val seekStepMs = buttonSeekSeconds * 1000L
                                val target = (current + seekStepMs).coerceAtMost(total)
                                lastSeekTargetMs = target
                                lastSeekTimeMs = System.currentTimeMillis()
                                exoPlayer.seekTo(target)
                            },
                            enabled = isSeekable,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSeekable) sideControlBg else sideControlBg.copy(alpha = 0.3f))
                                .border(1.dp, if (isSeekable) sideBorderColor else sideBorderColor.copy(alpha = 0.15f), CircleShape)
                        ) {
                            val forwardIcon = when (buttonSeekSeconds) {
                                5 -> Icons.Default.Forward5
                                30 -> Icons.Default.Forward30
                                else -> Icons.Default.Forward10
                            }
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = forwardIcon,
                                    contentDescription = "Fast Forward ${buttonSeekSeconds}s",
                                    tint = if (isSeekable) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                                if (buttonSeekSeconds != 5 && buttonSeekSeconds != 10 && buttonSeekSeconds != 30) {
                                    Text(
                                        text = "$buttonSeekSeconds",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSeekable) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        // 5. Next Video Button
                        val isNextEnabled = currentQueueIndex < playbackQueue.size - 1
                        IconButton(
                            onClick = {
                                resetControlsTimeout()
                                viewModel.playNext()
                            },
                            enabled = isNextEnabled,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isNextEnabled) sideControlBg else sideControlBg.copy(alpha = 0.3f))
                                .border(1.dp, if (isNextEnabled) sideBorderColor else sideBorderColor.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = if (isNextEnabled) sideIconTint else sideIconTint.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Seek Slider (holding remaining width)
                        val sliderPos = if (duration > 0) currentPos.toFloat() / duration else 0f
                        val primaryColor = MaterialTheme.colorScheme.primary
                        Slider(
                            value = sliderPos,
                            enabled = isSeekable,
                            onValueChange = {
                                resetControlsTimeout()
                                isSliderDragging = true
                                val target = (it * duration).toLong()
                                currentPos = target
                                if (!activeFastSeek) {
                                    lastSeekTargetMs = target
                                    lastSeekTimeMs = System.currentTimeMillis()
                                    exoPlayer.seekTo(target)
                                }
                            },
                            onValueChangeFinished = {
                                isSliderDragging = false
                                android.util.Log.d("PlayerScreen", "Slider change finished: currentPos=$currentPos, activeFastSeek=$activeFastSeek, seekable=${exoPlayer.isCurrentMediaItemSeekable}")
                                if (activeFastSeek) {
                                    lastSeekTargetMs = currentPos
                                    lastSeekTimeMs = System.currentTimeMillis()
                                    exoPlayer.seekTo(currentPos)
                                }
                            },
                            track = { sliderState ->
                                val fraction = sliderState.value
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                ) {
                                    val width = size.width
                                    val centerY = size.height / 2f
                                    val activeWidth = width * fraction
                                    
                                    // Inactive track: straight line
                                    drawLine(
                                        color = androidx.compose.ui.graphics.Color.DarkGray,
                                        start = Offset(activeWidth, centerY),
                                        end = Offset(width, centerY),
                                        strokeWidth = 5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    
                                    // Active track: wavy curve
                                    if (activeWidth > 0f) {
                                        val path = Path()
                                        path.moveTo(0f, centerY)
                                        val wavelength = 24.dp.toPx()
                                        val amplitude = waveAmplitude.toPx()
                                        val dampingDistance = 36.dp.toPx() // 1.5 wavelengths damping zone
                                        val step = 4f
                                        var x = 0f
                                        while (x < activeWidth) {
                                            val nextX = (x + step).coerceAtMost(activeWidth)
                                            
                                            // Damp wave amplitude at the end (near thumb)
                                            val distanceToThumb = activeWidth - x
                                            val endDamping = if (distanceToThumb < dampingDistance) {
                                                distanceToThumb / dampingDistance
                                            } else {
                                                1f
                                            }
                                            
                                            // Damp wave amplitude at the start (near 0)
                                            val distanceToStart = x
                                            val startDamping = if (distanceToStart < dampingDistance) {
                                                distanceToStart / dampingDistance
                                            } else {
                                                1f
                                            }
                                            
                                            val currentAmplitude = amplitude * endDamping * startDamping
                                            val angle = (2f * Math.PI.toFloat() * x / wavelength) - phaseShift
                                            val y = centerY + currentAmplitude * kotlin.math.sin(angle)
                                            path.lineTo(nextX, y)
                                            x = nextX
                                        }
                                        path.lineTo(activeWidth, centerY)
                                        drawPath(
                                            path = path,
                                            color = primaryColor,
                                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            },
                            thumb = { sliderState ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp), // Larger touch target
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp) // Larger visible circle dot
                                            .background(color = primaryColor, shape = CircleShape)
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_seek_slider")
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        // Time display on the right end
                        Text(
                            text = if (video.isStream) "LIVE" else formatDynamicTime(currentPos, duration),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remaining utility icons at the very bottom on the right side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

        // ── Gesture HUD Overlay (brightness / volume / seek feedback) ─────────
        GestureHudOverlay(
            visible = showGestureHud,
            gestureType = activeGestureType,
            brightness = gestureBrightness,
            volume = gestureVolume,
            seekOffsetMs = gestureSeekOffset,
            seekBasePos = dragStartPos
        )
        // ─────────────────────────────────────────────────────────────────────

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

        AnimatedVisibility(
            visible = decoderNotificationText != null,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 120.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lastDecoderNotificationText,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showSubtitleDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showSubtitleDialog = false
                    }
            )
        }

        AnimatedVisibility(
            visible = showSubtitleDialog,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier
                .width(280.dp)
                .align(Alignment.BottomEnd)
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtitles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showSubtitleDialog = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // List items (grows with items, up to max height, then scrolls)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Option: Off
                        val isNoneSelected = !activeSubtitles || exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .selectable(
                                    selected = isNoneSelected,
                                    onClick = {
                                        activeSubtitles = false
                                        isSubtitleDisabled = true
                                        selectedSubtitleGroupIndex = null
                                        selectedSubtitleTrackIndex = null
                                        selectedSubtitleLanguage = null
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        showSubtitleDialog = false
                                        saveCurrentProgress()
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(14.dp)
                                    .border(1.5.dp, if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isNoneSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Off",
                                fontSize = 14.sp,
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isNoneSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        // Inbuilt tracks
                        subtitleTracks.forEachIndexed { index, trackInfo ->
                            val isTrackSelected = !isNoneSelected && trackInfo.isSelected
                            val lang = trackInfo.format.language?.ifBlank { null } ?: "Unknown"
                            val label = trackInfo.format.label?.ifBlank { null }
                            val trackNum = index + 1
                            val name = when {
                                label != null -> "$label ($lang)"
                                lang != "Unknown" -> "Track $trackNum ($lang)"
                                else -> "Track $trackNum"
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp)
                                    .selectable(
                                        selected = isTrackSelected,
                                        onClick = {
                                            activeSubtitles = true
                                            isSubtitleDisabled = false
                                            selectedSubtitleGroupIndex = trackInfo.groupIndex
                                            selectedSubtitleTrackIndex = trackInfo.trackIndex
                                            selectedSubtitleLanguage = trackInfo.format.language
                                            val trackGroup = exoPlayer.currentTracks.groups[trackInfo.groupIndex].mediaTrackGroup
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .addOverride(TrackSelectionOverride(trackGroup, trackInfo.trackIndex))
                                                .build()
                                            showSubtitleDialog = false
                                            saveCurrentProgress()
                                        }
                                    )
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .size(14.dp)
                                        .border(1.5.dp, if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isTrackSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isTrackSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }

                        Divider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Option: Select external subtitle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clickable {
                                    showSubtitleDialog = false
                                    openFilePicker()
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Select external subtitle...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showNoSubtitlesPrompt) {
            AlertDialog(
                onDismissRequest = { showNoSubtitlesPrompt = false },
                title = {
                    Text(
                        text = "No Inbuilt Subtitles",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "This video has no inbuilt subtitles. Do you want to choose one from your file storage?",
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNoSubtitlesPrompt = false
                            openFilePicker()
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showNoSubtitlesPrompt = false }
                    ) {
                        Text("No")
                    }
                }
            )
        }

        if (playbackErrorMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .padding(24.dp)
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 400.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "Playback Failed",
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Text(
                        text = playbackErrorMsg!!,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            playbackErrorMsg = null
                            exoPlayer.release()
                            viewModel.clearActivePlayback()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Go Back",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (showAudioTrackDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showAudioTrackDialog = false
                    }
            )
        }

        AnimatedVisibility(
            visible = showAudioTrackDialog,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier
                .width(280.dp)
                .align(Alignment.BottomEnd)
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Tracks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showAudioTrackDialog = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // List items (grows with items, up to max height, then scrolls)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (audioTracks.isEmpty()) {
                            Text("No audio tracks available", fontSize = 14.sp)
                        } else {
                            audioTracks.forEachIndexed { index, trackInfo ->
                                val lang = trackInfo.format.language?.ifBlank { null } ?: "Unknown"
                                val label = trackInfo.format.label?.ifBlank { null }
                                val trackNum = index + 1
                                val name = when {
                                    label != null -> "$label ($lang)"
                                    lang != "Unknown" -> "Track $trackNum ($lang)"
                                    else -> "Track $trackNum"
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 36.dp)
                                        .selectable(
                                            selected = trackInfo.isSelected,
                                            onClick = {
                                                selectedAudioGroupIndex = trackInfo.groupIndex
                                                selectedAudioTrackIndex = trackInfo.trackIndex
                                                selectedAudioLanguage = trackInfo.format.language
                                                val trackGroup = exoPlayer.currentTracks.groups[trackInfo.groupIndex].mediaTrackGroup
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                    .addOverride(TrackSelectionOverride(trackGroup, trackInfo.trackIndex))
                                                    .build()
                                                showAudioTrackDialog = false
                                                saveCurrentProgress()
                                            }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(14.dp)
                                            .border(1.5.dp, if (trackInfo.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (trackInfo.isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = name,
                                        fontSize = 14.sp,
                                        color = if (trackInfo.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (trackInfo.isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDecoderDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showDecoderDialog = false
                    }
            )
        }

        AnimatedVisibility(
            visible = showDecoderDialog,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier
                .width(280.dp)
                .align(Alignment.BottomEnd)
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentHeight()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    bottomStart = 16.dp,
                    topEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Decoder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { showDecoderDialog = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // List items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: Hardware Decoder (HW)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = hardwareAccel,
                                    onClick = {
                                        if (!hardwareAccel) {
                                            viewModel.toggleHardwareAcceleration()
                                            decoderNotificationText = "Decoder: Hardware (HW)"
                                        }
                                        showDecoderDialog = false
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(14.dp)
                                    .border(1.5.dp, if (hardwareAccel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hardwareAccel) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hardware Decoder (HW)",
                                    fontSize = 14.sp,
                                    color = if (hardwareAccel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (hardwareAccel) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "Uses device hardware decoders (efficient, but formats like Dolby EAC3 might not play)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Option 2: Software Decoder (SW)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = !hardwareAccel,
                                    onClick = {
                                        if (hardwareAccel) {
                                            viewModel.toggleHardwareAcceleration()
                                            decoderNotificationText = "Decoder: Software (SW)"
                                        }
                                        showDecoderDialog = false
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(14.dp)
                                    .border(1.5.dp, if (!hardwareAccel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!hardwareAccel) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Software Decoder (SW)",
                                    fontSize = 14.sp,
                                    color = if (!hardwareAccel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (!hardwareAccel) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "Uses FFmpeg extension (supports Dolby EAC3/Atmos & high-end formats)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading || isPendingPostSwipe) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (isLoading) 0.5f else 0.1f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                if (isLoading || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            }
        }

        // Floating orientation suggestion button (rotated dynamically based on target orientation)
        val bottomPadding by animateDpAsState(
            targetValue = if (showControls) 128.dp else 24.dp,
            animationSpec = tween(durationMillis = 300),
            label = "orientation_button_bottom_padding"
        )

        AnimatedVisibility(
            visible = showOrientationSuggestion && !showSubtitleDialog && !showAudioTrackDialog && !showDecoderDialog,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding, end = 24.dp)
        ) {
            val rotationAnim = remember { Animatable(if (suggestedTargetOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) 0f else -90f) }
            LaunchedEffect(showOrientationSuggestion, suggestedTargetOrientation) {
                if (showOrientationSuggestion) {
                    val startAngle = if (suggestedTargetOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) 0f else -90f
                    val endAngle = if (suggestedTargetOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) -90f else 0f
                    repeat(2) {
                        rotationAnim.snapTo(startAngle)
                        rotationAnim.animateTo(
                            targetValue = endAngle,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                        delay(1000L)
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    val activity = context as? Activity
                    activity?.requestedOrientation = suggestedTargetOrientation
                    showOrientationSuggestion = false
                    lastPhysicalOrientation = if (suggestedTargetOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        DeviceOrientation.PORTRAIT
                    } else {
                        DeviceOrientation.LANDSCAPE
                    }
                },
                containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = "Change orientation suggestion",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer(rotationZ = rotationAnim.value)
                )
            }
        }

        // Custom Snackbar overlay
        AnimatedVisibility(
            visible = snackbarMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(0.8f),
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
                        text = snackbarMessage ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─── Gesture HUD Overlay ──────────────────────────────────────────────────────
/**
 * Renders real-time feedback panels for the three gesture zones:
 *  - Brightness  → vertical bar, left-center of screen
 *  - Volume      → vertical bar, right-center of screen
 *  - Seek        → rounded pill, exact center of screen
 *
 * All three panels are independent [AnimatedVisibility] nodes so they never
 * conflict with each other or with the main control overlay.
 */
@Composable
private fun GestureHudOverlay(
    visible: Boolean,
    gestureType: GestureType?,
    brightness: Float,
    volume: Float,
    seekOffsetMs: Long,
    seekBasePos: Long   // stable drag-start position — used for "from" timestamp
) {
    val barWidth = 24.dp
    val barHeight = 130.dp

    // Theme color palette tokens
    val cardColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    // ── Brightness HUD (Swapped to RIGHT-CENTER: user swipes left side of screen) ──
    AnimatedVisibility(
        visible = visible && gestureType == GestureType.BRIGHTNESS,
        enter = fadeIn(animationSpec = tween(150)),
        exit  = fadeOut(animationSpec = tween(300)),
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.CenterEnd)
            .padding(end = 28.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(primaryContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Brightness",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Vertical bar: filled bottom-to-top using theme gradient
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(onSurfaceColor.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(brightness.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(primaryColor, tertiaryColor)
                                )
                            )
                            .align(Alignment.BottomCenter)
                    )
                }

                Text(
                    text = "${(brightness * 100).toInt()}",
                    color = onSurfaceColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ── Volume HUD (Swapped to LEFT-CENTER: user swipes right side of screen) ──────
    AnimatedVisibility(
        visible = visible && gestureType == GestureType.VOLUME,
        enter = fadeIn(animationSpec = tween(150)),
        exit  = fadeOut(animationSpec = tween(300)),
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.CenterStart)
            .padding(start = 28.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(primaryContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Volume",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(onSurfaceColor.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(volume.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(primaryColor, tertiaryColor)
                                )
                            )
                            .align(Alignment.BottomCenter)
                    )
                }

                Text(
                    text = "${(volume * 100).toInt()}",
                    color = onSurfaceColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ── Seek HUD (Exact Center of Screen) ───────────────────────────────────
    AnimatedVisibility(
        visible = visible && gestureType == GestureType.SEEK,
        enter = fadeIn(animationSpec = tween(150)),
        exit  = fadeOut(animationSpec = tween(300)),
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        val seekSecs = seekOffsetMs / 1000L
        val projectedPos = (seekBasePos + seekOffsetMs).coerceAtLeast(0L)
        val arrow = if (seekOffsetMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind
        val sign  = if (seekOffsetMs >= 0) "+" else ""

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = arrow,
                        contentDescription = "Seek",
                        tint = primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "${sign}${seekSecs}s",
                        color = onSurfaceColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${formatTime(seekBasePos)} → ${formatTime(projectedPos)}",
                    color = onSurfaceColor.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────

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

private fun formatDynamicTime(currentMs: Long, totalMs: Long): String {
    val totalSecs = (totalMs / 1000).coerceAtLeast(0L)
    val totalHours = totalSecs / 3600
    val totS = totalSecs % 60
    val totM = (totalSecs / 60) % 60
    val totH = totalHours

    val currentSecs = (currentMs / 1000).coerceAtLeast(0L)
    val curS = currentSecs % 60
    val curM = (currentSecs / 60) % 60
    val curH = currentSecs / 3600

    return if (totalHours > 0) {
        String.format("%02d:%02d:%02d/%02d:%02d:%02d", curH, curM, curS, totH, totM, totS)
    } else {
        String.format("%02d:%02d/%02d:%02d", curM, curS, totM, totS)
    }
}

private enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
    UNKNOWN
}

@androidx.media3.common.util.UnstableApi
private class CustomMediaSourceFactory(
    context: android.content.Context,
    dataSourceFactory: androidx.media3.datasource.DataSource.Factory
) : androidx.media3.exoplayer.source.MediaSource.Factory {
    
    private val defaultFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
        .experimentalParseSubtitlesDuringExtraction(false)
        
    private val hlsFactory = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(false)

    override fun createMediaSource(mediaItem: androidx.media3.common.MediaItem): androidx.media3.exoplayer.source.MediaSource {
        val mimeType = mediaItem.localConfiguration?.mimeType
        val uriPath = mediaItem.localConfiguration?.uri?.path
        val isHls = mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8 ||
                uriPath?.endsWith(".m3u8") == true ||
                uriPath?.contains(".m3u8") == true
        return if (isHls) {
            hlsFactory.createMediaSource(mediaItem)
        } else {
            defaultFactory.createMediaSource(mediaItem)
        }
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider): androidx.media3.exoplayer.source.MediaSource.Factory {
        defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        hlsFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy): androidx.media3.exoplayer.source.MediaSource.Factory {
        defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        hlsFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray {
        return defaultFactory.supportedTypes
    }
}

@androidx.media3.common.util.UnstableApi
private class ChannelMaskSanitizerAudioProcessor : androidx.media3.common.audio.AudioProcessor {
    private var pendingOutputFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    private var outputFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    private var buffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        pendingOutputFormat = inputAudioFormat
        return pendingOutputFormat
    }

    override fun isActive(): Boolean {
        return pendingOutputFormat != androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        if (buffer.capacity() < remaining) {
            buffer = java.nio.ByteBuffer.allocateDirect(remaining).order(java.nio.ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        buffer.put(inputBuffer)
        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): java.nio.ByteBuffer {
        val output = outputBuffer
        outputBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        pendingOutputFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
        outputFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    }
}
