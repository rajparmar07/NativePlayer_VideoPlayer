package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTheme {
    Light, Dark, System
}

enum class SubtitleStyle {
    Default, ClassicWhite, WarmYellow, CyanOutline, WhiteOnBlackBox, YellowOnBlackBox
}

class VideoPlayerViewModel(
    private val repository: VideoRepository,
    context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("app_theme", AppTheme.System.name) ?: AppTheme.System.name)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    private val _subtitleStyle = MutableStateFlow(
        SubtitleStyle.valueOf(prefs.getString("subtitle_style", SubtitleStyle.Default.name) ?: SubtitleStyle.Default.name)
    )
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    fun setSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
        prefs.edit().putString("subtitle_style", style.name).apply()
    }

    private val _resumeFromLastLeftEnabled = MutableStateFlow(
        prefs.getBoolean("resume_from_last_left", true)
    )
    val resumeFromLastLeftEnabled: StateFlow<Boolean> = _resumeFromLastLeftEnabled.asStateFlow()

    fun setResumeFromLastLeftEnabled(enabled: Boolean) {
        _resumeFromLastLeftEnabled.value = enabled
        prefs.edit().putBoolean("resume_from_last_left", enabled).apply()
    }

    private val _playerControllerTimeout = MutableStateFlow(
        prefs.getInt("player_controller_timeout", 4)
    )
    val playerControllerTimeout: StateFlow<Int> = _playerControllerTimeout.asStateFlow()

    fun setPlayerControllerTimeout(seconds: Int) {
        val bounded = seconds.coerceIn(1, 60)
        _playerControllerTimeout.value = bounded
        prefs.edit().putInt("player_controller_timeout", bounded).apply()
    }

    private val _pipEnabled = MutableStateFlow(
        prefs.getBoolean("pip_enabled", true)
    )
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    fun setPipEnabled(enabled: Boolean) {
        _pipEnabled.value = enabled
        prefs.edit().putBoolean("pip_enabled", enabled).apply()
    }

    private val _thumbnailFrameTimeUs = MutableStateFlow(
        prefs.getLong("thumbnail_frame_time_us", 400_000L)
    )
    val thumbnailFrameTimeUs: StateFlow<Long> = _thumbnailFrameTimeUs.asStateFlow()

    fun setThumbnailFrameTimeUs(us: Long) {
        _thumbnailFrameTimeUs.value = us
        prefs.edit().putLong("thumbnail_frame_time_us", us).apply()
    }

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    private val _isVideoPlaying = MutableStateFlow(false)
    val isVideoPlaying: StateFlow<Boolean> = _isVideoPlaying.asStateFlow()

    fun setVideoPlaying(playing: Boolean) {
        _isVideoPlaying.value = playing
    }

    private val _videoWidth = MutableStateFlow(16)
    val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(9)
    val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    fun setVideoDimensions(width: Int, height: Int) {
        _videoWidth.value = width.coerceAtLeast(1)
        _videoHeight.value = height.coerceAtLeast(1)
    }

    enum class PlaybackCommand {
        PLAY, PAUSE
    }

    private val _playbackCommand = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 1)
    val playbackCommand = _playbackCommand.asSharedFlow()

    fun play() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.PLAY)
        }
    }

    fun pause() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.PAUSE)
        }
    }

    private val _videoProgressMap = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val videoProgressMap: StateFlow<Map<String, Pair<Long, Long>>> = _videoProgressMap.asStateFlow()

    init {
        _videoProgressMap.value = loadVideoProgressMap()
    }

    private fun loadVideoProgressMap(): Map<String, Pair<Long, Long>> {
        val map = mutableMapOf<String, Pair<Long, Long>>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("progress_") && value is String) {
                val urlOrPath = key.substringAfter("progress_")
                val parts = value.split(":")
                if (parts.size == 2) {
                    val progress = parts[0].toLongOrNull()
                    val duration = parts[1].toLongOrNull()
                    if (progress != null && duration != null) {
                        map[urlOrPath] = Pair(progress, duration)
                    }
                }
            }
        }
        return map
    }

    fun saveVideoProgress(urlOrPath: String, progressMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val isWatchedWhole = progressMs >= durationMs - 5000 && progressMs >= (durationMs * 0.95).toLong()
        if (isWatchedWhole) {
            prefs.edit().remove("progress_$urlOrPath").commit()
        } else {
            prefs.edit().putString("progress_$urlOrPath", "$progressMs:$durationMs").commit()
        }
        _videoProgressMap.value = loadVideoProgressMap()
    }

    // Local Videos State
    private val _localVideos = MutableStateFlow<List<VideoModel>>(emptyList())
    val localVideos: StateFlow<List<VideoModel>> = _localVideos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Local Library persistent UI States
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    fun setSelectedFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
    }

    // Active Playback states
    private val _currentPlayingVideo = MutableStateFlow<VideoModel?>(null)
    val currentPlayingVideo: StateFlow<VideoModel?> = _currentPlayingVideo.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<VideoModel>>(emptyList())
    val playbackQueue: StateFlow<List<VideoModel>> = _playbackQueue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    // Hardware Acceleration setting toggles
    private val _hardwareAccelerationEnabled = MutableStateFlow(true)
    val hardwareAccelerationEnabled: StateFlow<Boolean> = _hardwareAccelerationEnabled.asStateFlow()

    // Fast Seek setting logic (defaults to false per request)
    private val _fastSeekEnabled = MutableStateFlow(
        prefs.getBoolean("fast_seek_enabled", false)
    )
    val fastSeekEnabled: StateFlow<Boolean> = _fastSeekEnabled.asStateFlow()

    fun setFastSeekEnabled(enabled: Boolean) {
        _fastSeekEnabled.value = enabled
        prefs.edit().putBoolean("fast_seek_enabled", enabled).apply()
    }

    // Gesture seek sensitivity (configurable, later exposed in Settings)
    private val _gestureSeekMs = MutableStateFlow(
        prefs.getLong("gesture_seek_ms", 2000L)
    )
    val gestureSeekMs: StateFlow<Long> = _gestureSeekMs.asStateFlow()

    fun setGestureSeekMs(ms: Long) {
        _gestureSeekMs.value = ms
        prefs.edit().putLong("gesture_seek_ms", ms).apply()
    }

    private val _seekGestureEnabled = MutableStateFlow(
        prefs.getBoolean("seek_gesture_enabled", true)
    )
    val seekGestureEnabled: StateFlow<Boolean> = _seekGestureEnabled.asStateFlow()

    fun setSeekGestureEnabled(enabled: Boolean) {
        _seekGestureEnabled.value = enabled
        prefs.edit().putBoolean("seek_gesture_enabled", enabled).apply()
    }

    private val _volumeGestureEnabled = MutableStateFlow(
        prefs.getBoolean("volume_gesture_enabled", true)
    )
    val volumeGestureEnabled: StateFlow<Boolean> = _volumeGestureEnabled.asStateFlow()

    fun setVolumeGestureEnabled(enabled: Boolean) {
        _volumeGestureEnabled.value = enabled
        prefs.edit().putBoolean("volume_gesture_enabled", enabled).apply()
    }

    private val _brightnessGestureEnabled = MutableStateFlow(
        prefs.getBoolean("brightness_gesture_enabled", true)
    )
    val brightnessGestureEnabled: StateFlow<Boolean> = _brightnessGestureEnabled.asStateFlow()

    fun setBrightnessGestureEnabled(enabled: Boolean) {
        _brightnessGestureEnabled.value = enabled
        prefs.edit().putBoolean("brightness_gesture_enabled", enabled).apply()
    }

    private val _seekSwipePixels = MutableStateFlow(
        prefs.getInt("seek_swipe_pixels", 60)
    )
    val seekSwipePixels: StateFlow<Int> = _seekSwipePixels.asStateFlow()

    fun setSeekSwipePixels(pixels: Int) {
        val bounded = pixels.coerceIn(20, 150)
        _seekSwipePixels.value = bounded
        prefs.edit().putInt("seek_swipe_pixels", bounded).apply()
    }

    private val _volumeSwipePixels = MutableStateFlow(
        prefs.getInt("volume_swipe_pixels", 1000)
    )
    val volumeSwipePixels: StateFlow<Int> = _volumeSwipePixels.asStateFlow()

    fun setVolumeSwipePixels(pixels: Int) {
        val bounded = pixels.coerceIn(200, 2000)
        _volumeSwipePixels.value = bounded
        prefs.edit().putInt("volume_swipe_pixels", bounded).apply()
    }

    private val _brightnessSensitivity = MutableStateFlow(
        prefs.getFloat("brightness_sensitivity", 0.0015f)
    )
    val brightnessSensitivity: StateFlow<Float> = _brightnessSensitivity.asStateFlow()

    fun setBrightnessSensitivity(sensitivity: Float) {
        val bounded = sensitivity.coerceIn(0.0005f, 0.0050f)
        _brightnessSensitivity.value = bounded
        prefs.edit().putFloat("brightness_sensitivity", bounded).apply()
    }

    private val _zoomGestureEnabled = MutableStateFlow(
        prefs.getBoolean("zoom_gesture_enabled", true)
    )
    val zoomGestureEnabled: StateFlow<Boolean> = _zoomGestureEnabled.asStateFlow()

    fun setZoomGestureEnabled(enabled: Boolean) {
        _zoomGestureEnabled.value = enabled
        prefs.edit().putBoolean("zoom_gesture_enabled", enabled).apply()
    }

    private val _zoomSensitivity = MutableStateFlow(
        prefs.getFloat("zoom_sensitivity", 1.0f)
    )
    val zoomSensitivity: StateFlow<Float> = _zoomSensitivity.asStateFlow()

    fun setZoomSensitivity(sensitivity: Float) {
        val bounded = sensitivity.coerceIn(0.5f, 2.0f)
        _zoomSensitivity.value = bounded
        prefs.edit().putFloat("zoom_sensitivity", bounded).apply()
    }

    // Observe Room Database entities
    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<VideoDownload>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scanLocalVideos(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            val videos = repository.getLocalVideos(context)
            _localVideos.value = videos
            _isScanning.value = false
        }
    }

    // Playback state managers
    fun playVideo(video: VideoModel) {
        _currentPlayingVideo.value = video
        _playbackQueue.value = listOf(video)
        _currentQueueIndex.value = 0
    }

    fun playPlaylist(items: List<VideoModel>, startIndex: Int = 0) {
        if (items.isNotEmpty()) {
            _playbackQueue.value = items
            _currentQueueIndex.value = startIndex.coerceIn(0, items.size - 1)
            _currentPlayingVideo.value = items[_currentQueueIndex.value]
        }
    }

    fun playNext() {
        val queue = _playbackQueue.value
        val currentIndex = _currentQueueIndex.value
        if (queue.isNotEmpty() && currentIndex < queue.size - 1) {
            val nextIndex = currentIndex + 1
            _currentQueueIndex.value = nextIndex
            _currentPlayingVideo.value = queue[nextIndex]
        }
    }

    fun playPrevious() {
        val queue = _playbackQueue.value
        val currentIndex = _currentQueueIndex.value
        if (queue.isNotEmpty() && currentIndex > 0) {
            val prevIndex = currentIndex - 1
            _currentQueueIndex.value = prevIndex
            _currentPlayingVideo.value = queue[prevIndex]
        }
    }

    fun clearActivePlayback() {
        _currentPlayingVideo.value = null
        _playbackQueue.value = emptyList()
        _currentQueueIndex.value = -1
    }

    fun toggleHardwareAcceleration() {
        _hardwareAccelerationEnabled.value = !_hardwareAccelerationEnabled.value
    }

    // Database Actions
    fun createPlaylist(title: String, description: String) {
        viewModelScope.launch {
            repository.createPlaylist(title, description)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return repository.getItemsForPlaylist(playlistId)
    }

    fun addVideoToPlaylist(playlistId: Long, title: String, urlOrPath: String, subtitleUrl: String? = null) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, title, urlOrPath, subtitleUrl)
        }
    }

    fun deletePlaylistItem(itemId: Long) {
        viewModelScope.launch {
            repository.deletePlaylistItem(itemId)
        }
    }

    fun startDownload(
        context: Context,
        title: String,
        videoUrl: String,
        subtitleUrl: String? = null,
        destinationPath: String? = null
    ) {
        viewModelScope.launch {
            repository.startDownload(context, title, videoUrl, subtitleUrl, destinationPath)
        }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            repository.deleteDownload(downloadId)
        }
    }

    fun deleteDownloadByFilePath(filePath: String) {
        viewModelScope.launch {
            repository.deleteDownloadByFilePath(filePath)
        }
    }
}

class VideoPlayerViewModelFactory(
    private val repository: VideoRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoPlayerViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
