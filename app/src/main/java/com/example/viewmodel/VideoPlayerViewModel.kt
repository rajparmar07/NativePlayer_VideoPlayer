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

    // Local Videos State
    private val _localVideos = MutableStateFlow<List<VideoModel>>(emptyList())
    val localVideos: StateFlow<List<VideoModel>> = _localVideos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

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

    fun startDownload(context: Context, title: String, videoUrl: String, subtitleUrl: String? = null) {
        viewModelScope.launch {
            repository.startDownload(context, title, videoUrl, subtitleUrl)
        }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            repository.deleteDownload(downloadId)
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
