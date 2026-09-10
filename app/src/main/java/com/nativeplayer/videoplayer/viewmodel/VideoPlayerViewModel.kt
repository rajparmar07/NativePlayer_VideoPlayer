package com.nativeplayer.videoplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nativeplayer.videoplayer.data.*
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTheme {
    Light, Dark, System, Custom
}

enum class AppFontSize(val scaleFactor: Float, val label: String) {
    Small(0.70f, "Small (70%)"),
    Medium(0.85f, "Medium (85%)"),
    Regular(1.00f, "Regular (100%)"),
    Large(1.15f, "Large (115%)")
}

enum class AppThemePalette(val title: String, val subtitle: String, val isLightPalette: Boolean) {
    SageMint("Sage Mint", "Light · Soft mint green & fresh teal", true),
    SoftLavender("Soft Lavender", "Light · Gentle lavender & warm amber", true),
    WarmSand("Warm Sand", "Light · Soothing sand & coral rose", true),
    OceanSlate("Ocean Slate", "Dark · Soft slate navy & sky cyan", false),
    NordicIndigo("Nordic Indigo", "Dark · Deep indigo night & ice cyan", false),
    RoseQuartz("Rose Quartz", "Dark · Soft dusk rose & champagne gold", false)
}

enum class SubtitleStyle {
    Default, ClassicWhite, WarmYellow, CyanOutline, WhiteOnBlackBox, YellowOnBlackBox
}

enum class ThumbnailMode(val title: String, val description: String) {
    FIRST_FRAME("First Frame (0s)", "Captures the very first frame of the video file"),
    PERCENTAGE("Percentage of Duration", "Extracts frame based on custom video length percentage"),
    LAST_PLAYED("Last Played Position", "Uses last played position; falls back to percentage for unplayed videos")
}

enum class ListDisplayMode(val label: String) {
    FOLDERS("Folders"),
    MEMORY_TREE("Memory Tree")
}

enum class ListStyle(val label: String) {
    LIST("List"),
    GRID("Grid")
}

enum class GridColumns(val count: Int, val label: String) {
    TWO(2, "2 Columns"),
    THREE(3, "3 Columns"),
    FOUR(4, "4 Columns")
}

enum class SortField(val label: String) {
    NAME("Name"),
    DATE("Date Modified"),
    SIZE("Size"),
    DURATION("Duration")
}

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class VideoTileInfo(val label: String, val description: String) {
    MINIMAL("Minimal", "Video Name and Path"),
    ESSENTIAL("Essential", "Minimal + Duration and Size"),
    ADVANCED("Advanced", "Essential + Video type, Resolution and Seek position")
}

enum class LongPressMode(val label: String, val description: String) {
    WHOLE_SCREEN("Whole Screen", "Long press anywhere on whole screen to play at selected speed"),
    SPLIT_SCREEN("Split Screen (Left / Right)", "Long press right side to forward, left side to backward at selected speed")
}

enum class PlaylistThumbnailPattern(val label: String, val description: String) {
    FIRST_VIDEO("First Video in Playlist", "Uses the thumbnail of the first video in the playlist as the cover"),
    LAST_VIDEO("Last Video in Playlist", "Uses the thumbnail of the last added video in the playlist as the cover")
}

enum class ScreenshotLocation(val label: String, val description: String) {
    APP_FOLDER("App folder", "Pictures/NativePlayer"),
    SCREENSHOTS("Screenshots folder", "Pictures/Screenshots (Unified with system screenshots)")
}

data class DisplaySettings(
    val displayMode: ListDisplayMode = ListDisplayMode.FOLDERS,
    val listStyle: ListStyle = ListStyle.LIST,
    val gridColumns: GridColumns = GridColumns.THREE,
    val sortField: SortField = SortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val videoTileInfo: VideoTileInfo = VideoTileInfo.ESSENTIAL
)

class VideoPlayerViewModel(
    private val repository: VideoRepository,
    context: Context
) : ViewModel() {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _playlistThumbnailPattern = MutableStateFlow(
        runCatching {
            PlaylistThumbnailPattern.valueOf(
                prefs.getString("playlist_thumbnail_pattern", PlaylistThumbnailPattern.FIRST_VIDEO.name)
                    ?: PlaylistThumbnailPattern.FIRST_VIDEO.name
            )
        }.getOrDefault(PlaylistThumbnailPattern.FIRST_VIDEO)
    )
    val playlistThumbnailPattern: StateFlow<PlaylistThumbnailPattern> = _playlistThumbnailPattern.asStateFlow()

    fun setPlaylistThumbnailPattern(pattern: PlaylistThumbnailPattern) {
        _playlistThumbnailPattern.value = pattern
        prefs.edit().putString("playlist_thumbnail_pattern", pattern.name).apply()
    }

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    fun setSelectedPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    private val _appTheme = MutableStateFlow(
        runCatching { AppTheme.valueOf(prefs.getString("app_theme", AppTheme.System.name) ?: AppTheme.System.name) }.getOrDefault(AppTheme.System)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    private val _appFontSize = MutableStateFlow(
        runCatching {
            val savedName = prefs.getString("app_font_size", AppFontSize.Regular.name) ?: AppFontSize.Regular.name
            runCatching { AppFontSize.valueOf(savedName) }.getOrElse {
                when (savedName) {
                    "Normal" -> AppFontSize.Medium
                    "ExtraLarge" -> AppFontSize.Large
                    else -> AppFontSize.Regular
                }
            }
        }.getOrDefault(AppFontSize.Regular)
    )
    val appFontSize: StateFlow<AppFontSize> = _appFontSize.asStateFlow()

    fun setAppFontSize(fontSize: AppFontSize) {
        _appFontSize.value = fontSize
        prefs.edit().putString("app_font_size", fontSize.name).apply()
    }

    private val _appThemePalette = MutableStateFlow(
        runCatching { AppThemePalette.valueOf(prefs.getString("app_theme_palette", AppThemePalette.OceanSlate.name) ?: AppThemePalette.OceanSlate.name) }.getOrDefault(AppThemePalette.OceanSlate)
    )
    val appThemePalette: StateFlow<AppThemePalette> = _appThemePalette.asStateFlow()

    fun setAppThemePalette(palette: AppThemePalette) {
        _appThemePalette.value = palette
        prefs.edit().putString("app_theme_palette", palette.name).apply()
    }

    private val _isHighContrastDark = MutableStateFlow(
        prefs.getBoolean("high_contrast_dark", false)
    )
    val isHighContrastDark: StateFlow<Boolean> = _isHighContrastDark.asStateFlow()

    fun setIsHighContrastDark(enabled: Boolean) {
        _isHighContrastDark.value = enabled
        prefs.edit().putBoolean("high_contrast_dark", enabled).apply()
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

    private val _audioFocusEnabled = MutableStateFlow(
        prefs.getBoolean("audio_focus_enabled", true)
    )
    val audioFocusEnabled: StateFlow<Boolean> = _audioFocusEnabled.asStateFlow()

    fun setAudioFocusEnabled(enabled: Boolean) {
        _audioFocusEnabled.value = enabled
        prefs.edit().putBoolean("audio_focus_enabled", enabled).apply()
    }

    private val _pauseOnHeadphonesDisconnectEnabled = MutableStateFlow(
        prefs.getBoolean("pause_on_headphones_disconnect", true)
    )
    val pauseOnHeadphonesDisconnectEnabled: StateFlow<Boolean> = _pauseOnHeadphonesDisconnectEnabled.asStateFlow()

    fun setPauseOnHeadphonesDisconnectEnabled(enabled: Boolean) {
        _pauseOnHeadphonesDisconnectEnabled.value = enabled
        prefs.edit().putBoolean("pause_on_headphones_disconnect", enabled).apply()
    }

    private val _thumbnailMode = MutableStateFlow(
        run {
            val saved = prefs.getString("thumbnail_mode", ThumbnailMode.PERCENTAGE.name)
            try { ThumbnailMode.valueOf(saved ?: ThumbnailMode.PERCENTAGE.name) } catch (e: Exception) { ThumbnailMode.PERCENTAGE }
        }
    )
    val thumbnailMode: StateFlow<ThumbnailMode> = _thumbnailMode.asStateFlow()

    private val _thumbnailPercentage = MutableStateFlow(
        prefs.getInt("thumbnail_percentage", 15)
    )
    val thumbnailPercentage: StateFlow<Int> = _thumbnailPercentage.asStateFlow()

    fun setThumbnailMode(mode: ThumbnailMode) {
        _thumbnailMode.value = mode
        prefs.edit().putString("thumbnail_mode", mode.name).apply()
        clearThumbnailCache()
    }

    fun setThumbnailPercentage(percentage: Int) {
        val valid = percentage.coerceIn(1, 99)
        _thumbnailPercentage.value = valid
        prefs.edit().putInt("thumbnail_percentage", valid).apply()
        clearThumbnailCache()
    }

    fun clearThumbnailCache() {
        com.nativeplayer.videoplayer.ui.components.ThumbnailCache.clear()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.nativeplayer.videoplayer.ui.components.ThumbnailDiskCache.clear(appContext)
        }
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

    private val _selectedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedVideoIds: StateFlow<Set<String>> = _selectedVideoIds.asStateFlow()

    fun setSelectedVideoIds(ids: Set<String>) {
        _selectedVideoIds.value = ids
    }

    fun toggleSelectVideoId(id: String) {
        val current = _selectedVideoIds.value
        _selectedVideoIds.value = if (current.contains(id)) current - id else current + id
    }

    fun clearSelectedVideoIds() {
        _selectedVideoIds.value = emptySet()
    }

    private val _showBulkCopyDialog = MutableStateFlow(false)
    val showBulkCopyDialog: StateFlow<Boolean> = _showBulkCopyDialog.asStateFlow()
    fun setShowBulkCopyDialog(show: Boolean) { _showBulkCopyDialog.value = show }

    private val _showBulkMoveDialog = MutableStateFlow(false)
    val showBulkMoveDialog: StateFlow<Boolean> = _showBulkMoveDialog.asStateFlow()
    fun setShowBulkMoveDialog(show: Boolean) { _showBulkMoveDialog.value = show }

    private val _showBulkDeleteDialog = MutableStateFlow(false)
    val showBulkDeleteDialog: StateFlow<Boolean> = _showBulkDeleteDialog.asStateFlow()
    fun setShowBulkDeleteDialog(show: Boolean) { _showBulkDeleteDialog.value = show }

    private val _showDisplaySettingsDialog = MutableStateFlow(false)
    val showDisplaySettingsDialog: StateFlow<Boolean> = _showDisplaySettingsDialog.asStateFlow()
    fun setShowDisplaySettingsDialog(show: Boolean) { _showDisplaySettingsDialog.value = show }

    private val _isSearchingMode = MutableStateFlow(false)
    val isSearchingMode: StateFlow<Boolean> = _isSearchingMode.asStateFlow()
    fun setIsSearchingMode(searching: Boolean) {
        _isSearchingMode.value = searching
        if (!searching) {
            _searchQuery.value = ""
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getRootTreePath(): String {
        val videos = localVideos.value
        if (videos.isEmpty()) return "/storage/emulated/0"
        val paths = videos.map { File(it.urlOrPath).parentFile?.absolutePath ?: "" }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) return "/storage/emulated/0"

        var common = paths.first()
        for (p in paths) {
            while (!p.startsWith(common) && common.isNotEmpty()) {
                common = File(common).parentFile?.absolutePath ?: ""
            }
        }
        var rootCandidate = common.ifEmpty { "/storage/emulated/0" }
        while (rootCandidate.isNotEmpty()) {
            val hasDirectVids = videos.any { File(it.urlOrPath).parentFile?.absolutePath == rootCandidate }
            if (hasDirectVids) break
            val childSubdirs = videos.mapNotNull { video ->
                val p = File(video.urlOrPath).parentFile ?: return@mapNotNull null
                var curr: File? = p
                var child: File? = null
                while (curr != null) {
                    if (curr.absolutePath == rootCandidate && child != null) return@mapNotNull child.absolutePath
                    child = curr
                    curr = curr.parentFile
                }
                null
            }.distinct()
            if (childSubdirs.size == 1) {
                rootCandidate = childSubdirs.first()
            } else {
                break
            }
        }
        return if (rootCandidate.isEmpty()) "/storage/emulated/0" else rootCandidate
    }

    fun resolveParentBranchingPath(currPath: String, rootTreePath: String, localVideos: List<VideoModel>): String? {
        var parent = File(currPath).parentFile?.absolutePath ?: return null
        while (parent.startsWith(rootTreePath) && parent != rootTreePath) {
            val hasDirectVids = localVideos.any { File(it.urlOrPath).parentFile?.absolutePath == parent }
            if (hasDirectVids) return parent

            val childSubdirs = localVideos.mapNotNull { video ->
                val p = File(video.urlOrPath).parentFile ?: return@mapNotNull null
                var curr: File? = p
                var child: File? = null
                while (curr != null) {
                    if (curr.absolutePath == parent && child != null) return@mapNotNull child.absolutePath
                    child = curr
                    curr = curr.parentFile
                }
                null
            }.distinct()

            if (childSubdirs.size > 1) return parent
            parent = File(parent).parentFile?.absolutePath ?: return rootTreePath
        }
        return if (parent.startsWith(rootTreePath)) rootTreePath else null
    }

    enum class PlaybackCommand {
        PLAY, PAUSE, RESTART
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

    fun restartCurrent() {
        viewModelScope.launch {
            _playbackCommand.emit(PlaybackCommand.RESTART)
        }
    }

data class VideoPlaybackState(
    val progressMs: Long,
    val durationMs: Long,
    val audioGroupIndex: Int? = null,
    val audioTrackIndex: Int? = null,
    val audioLanguage: String? = null,
    val subtitleGroupIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
    val subtitleLanguage: String? = null,
    val isSubtitleDisabled: Boolean = true
)

    private val _videoProgressMap = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val videoProgressMap: StateFlow<Map<String, Pair<Long, Long>>> = _videoProgressMap.asStateFlow()

    init {
        _videoProgressMap.value = loadVideoProgressMap()
        viewModelScope.launch {
            repository.ensureDefaultBookmarksPlaylist()
        }
    }

    private fun loadVideoProgressMap(): Map<String, Pair<Long, Long>> {
        val map = mutableMapOf<String, Pair<Long, Long>>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("progress_") && value is String) {
                val urlOrPath = key.substringAfter("progress_")
                val parts = value.split(":")
                if (parts.size >= 2) {
                    val progress = parts[0].toLongOrNull()
                    val duration = parts[1].toLongOrNull()
                    if (progress != null && duration != null && duration > 0) {
                        val ratio = progress.toDouble() / duration.toDouble()
                        if (ratio in 0.03..0.97 && progress >= 2000L) {
                            map[urlOrPath] = Pair(progress, duration)
                        }
                    }
                }
            }
        }
        return map
    }

    fun getVideoPlaybackState(urlOrPath: String): VideoPlaybackState? {
        val stringVal = prefs.getString("progress_$urlOrPath", null) ?: return null
        val parts = stringVal.split(":")
        if (parts.size >= 2) {
            val progress = parts[0].toLongOrNull() ?: return null
            val duration = parts[1].toLongOrNull() ?: return null
            if (duration > 0) {
                val ratio = progress.toDouble() / duration.toDouble()
                if (ratio < 0.03 || ratio > 0.97 || progress < 2000L) {
                    return null
                }
            }
            var audioGroup: Int? = null
            var audioTrack: Int? = null
            var audioLang: String? = null
            var subGroup: Int? = null
            var subTrack: Int? = null
            var subLang: String? = null
            var isSubDisabled = true

            if (parts.size >= 9) {
                audioGroup = parts[2].toIntOrNull()
                audioTrack = parts[3].toIntOrNull()
                audioLang = parts[4].ifEmpty { null }
                subGroup = parts[5].toIntOrNull()
                subTrack = parts[6].toIntOrNull()
                subLang = parts[7].ifEmpty { null }
                isSubDisabled = parts[8] == "1"
            }
            return VideoPlaybackState(
                progressMs = progress,
                durationMs = duration,
                audioGroupIndex = audioGroup,
                audioTrackIndex = audioTrack,
                audioLanguage = audioLang,
                subtitleGroupIndex = subGroup,
                subtitleTrackIndex = subTrack,
                subtitleLanguage = subLang,
                isSubtitleDisabled = isSubDisabled
            )
        }
        return null
    }

    fun saveVideoProgress(
        urlOrPath: String,
        progressMs: Long,
        durationMs: Long,
        audioGroupIndex: Int? = null,
        audioTrackIndex: Int? = null,
        audioLanguage: String? = null,
        subtitleGroupIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        subtitleLanguage: String? = null,
        isSubtitleDisabled: Boolean = true
    ) {
        if (durationMs <= 0) return
        val ratio = progressMs.toDouble() / durationMs.toDouble()
        // If progress is < 3% or > 97% of total duration (or < 2 seconds), do not register under previously played
        if (ratio < 0.03 || ratio > 0.97 || progressMs < 2000L) {
            prefs.edit().remove("progress_$urlOrPath").apply()
        } else {
            val audioGroupStr = audioGroupIndex?.toString() ?: ""
            val audioTrackStr = audioTrackIndex?.toString() ?: ""
            val audioLangStr = audioLanguage ?: ""
            val subGroupStr = subtitleGroupIndex?.toString() ?: ""
            val subTrackStr = subtitleTrackIndex?.toString() ?: ""
            val subLangStr = subtitleLanguage ?: ""
            val subDisabledStr = if (isSubtitleDisabled) "1" else "0"

            val value = "$progressMs:$durationMs:$audioGroupStr:$audioTrackStr:$audioLangStr:$subGroupStr:$subTrackStr:$subLangStr:$subDisabledStr"
            prefs.edit().putString("progress_$urlOrPath", value).apply()
        }
        _videoProgressMap.value = loadVideoProgressMap()
    }

    // Local Videos State
    private val _localVideos = MutableStateFlow<List<VideoModel>>(emptyList())
    val localVideos: StateFlow<List<VideoModel>> = _localVideos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Display & Layout Settings persistent state
    private fun loadDisplaySettings(): DisplaySettings {
        val modeStr = prefs.getString("display_mode", ListDisplayMode.FOLDERS.name)
        val styleStr = prefs.getString("list_style", ListStyle.LIST.name)
        val gridColsStr = prefs.getString("grid_columns", GridColumns.THREE.name)
        val sortFieldStr = prefs.getString("sort_field", SortField.NAME.name)
        val sortDirStr = prefs.getString("sort_direction", SortDirection.ASCENDING.name)
        val tileInfoStr = prefs.getString("video_tile_info", VideoTileInfo.ESSENTIAL.name)

        val mode = runCatching { ListDisplayMode.valueOf(modeStr!!) }.getOrDefault(ListDisplayMode.FOLDERS)
        val style = runCatching { ListStyle.valueOf(styleStr!!) }.getOrDefault(ListStyle.LIST)
        val gridCols = runCatching { GridColumns.valueOf(gridColsStr!!) }.getOrDefault(GridColumns.THREE)
        val sortField = runCatching { SortField.valueOf(sortFieldStr!!) }.getOrDefault(SortField.NAME)
        val sortDir = runCatching { SortDirection.valueOf(sortDirStr!!) }.getOrDefault(SortDirection.ASCENDING)
        val tileInfo = runCatching { VideoTileInfo.valueOf(tileInfoStr!!) }.getOrDefault(VideoTileInfo.ESSENTIAL)

        return DisplaySettings(
            displayMode = mode,
            listStyle = style,
            gridColumns = gridCols,
            sortField = sortField,
            sortDirection = sortDir,
            videoTileInfo = tileInfo
        )
    }

    private val _displaySettings = MutableStateFlow(loadDisplaySettings())
    val displaySettings: StateFlow<DisplaySettings> = _displaySettings.asStateFlow()

    fun updateDisplaySettings(newSettings: DisplaySettings) {
        _displaySettings.value = newSettings
        prefs.edit()
            .putString("display_mode", newSettings.displayMode.name)
            .putString("list_style", newSettings.listStyle.name)
            .putString("grid_columns", newSettings.gridColumns.name)
            .putString("sort_field", newSettings.sortField.name)
            .putString("sort_direction", newSettings.sortDirection.name)
            .putString("video_tile_info", newSettings.videoTileInfo.name)
            .apply()
        _isGridView.value = (newSettings.listStyle == ListStyle.GRID)
    }

    // Local Library persistent UI States
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    fun setSelectedFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    private val _selectedTreePath = MutableStateFlow<String?>(null)
    val selectedTreePath: StateFlow<String?> = _selectedTreePath.asStateFlow()

    fun setSelectedTreePath(path: String?) {
        _selectedTreePath.value = path
    }

    private val _isGridView = MutableStateFlow(_displaySettings.value.listStyle == ListStyle.GRID)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
        val newStyle = if (isGrid) ListStyle.GRID else ListStyle.LIST
        if (_displaySettings.value.listStyle != newStyle) {
            updateDisplaySettings(_displaySettings.value.copy(listStyle = newStyle))
        }
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

    // Dynamic player surface skip button duration (defaults to 10s)
    private val _buttonSeekSeconds = MutableStateFlow(
        prefs.getInt("button_seek_seconds", 10)
    )
    val buttonSeekSeconds: StateFlow<Int> = _buttonSeekSeconds.asStateFlow()

    fun setButtonSeekSeconds(seconds: Int) {
        val validSeconds = if (seconds in listOf(5, 10, 15, 30, 60)) seconds else 10
        _buttonSeekSeconds.value = validSeconds
        prefs.edit().putInt("button_seek_seconds", validSeconds).apply()
    }

    // Screenshot save location setting
    private val _screenshotLocation = MutableStateFlow(
        runCatching {
            val saved = prefs.getString("screenshot_location", ScreenshotLocation.APP_FOLDER.name) ?: ScreenshotLocation.APP_FOLDER.name
            ScreenshotLocation.valueOf(saved)
        }.getOrDefault(ScreenshotLocation.APP_FOLDER)
    )
    val screenshotLocation: StateFlow<ScreenshotLocation> = _screenshotLocation.asStateFlow()

    fun setScreenshotLocation(location: ScreenshotLocation) {
        _screenshotLocation.value = location
        prefs.edit().putString("screenshot_location", location.name).apply()
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

    // Long press to play at nx speed gesture settings
    private val _longPressSpeedEnabled = MutableStateFlow(
        prefs.getBoolean("long_press_speed_enabled", true)
    )
    val longPressSpeedEnabled: StateFlow<Boolean> = _longPressSpeedEnabled.asStateFlow()

    fun setLongPressSpeedEnabled(enabled: Boolean) {
        _longPressSpeedEnabled.value = enabled
        prefs.edit().putBoolean("long_press_speed_enabled", enabled).apply()
    }

    private val _longPressMode = MutableStateFlow(
        runCatching {
            val saved = prefs.getString("long_press_mode", LongPressMode.WHOLE_SCREEN.name) ?: LongPressMode.WHOLE_SCREEN.name
            LongPressMode.valueOf(saved)
        }.getOrDefault(LongPressMode.WHOLE_SCREEN)
    )
    val longPressMode: StateFlow<LongPressMode> = _longPressMode.asStateFlow()

    fun setLongPressMode(mode: LongPressMode) {
        _longPressMode.value = mode
        prefs.edit().putString("long_press_mode", mode.name).apply()
    }

    private val _longPressWholeScreenSpeed = MutableStateFlow(
        prefs.getFloat("long_press_whole_speed", 2.0f).coerceIn(0.25f, 4.0f)
    )
    val longPressWholeScreenSpeed: StateFlow<Float> = _longPressWholeScreenSpeed.asStateFlow()

    fun setLongPressWholeScreenSpeed(speed: Float) {
        val bounded = (Math.round(speed.coerceIn(0.25f, 4.0f) * 100f) / 100f)
        _longPressWholeScreenSpeed.value = bounded
        prefs.edit().putFloat("long_press_whole_speed", bounded).apply()
    }

    private val _longPressLeftSpeed = MutableStateFlow(
        prefs.getFloat("long_press_left_speed", 2.0f).coerceIn(0.25f, 4.0f)
    )
    val longPressLeftSpeed: StateFlow<Float> = _longPressLeftSpeed.asStateFlow()

    fun setLongPressLeftSpeed(speed: Float) {
        val bounded = (Math.round(speed.coerceIn(0.25f, 4.0f) * 100f) / 100f)
        _longPressLeftSpeed.value = bounded
        prefs.edit().putFloat("long_press_left_speed", bounded).apply()
    }

    private val _longPressRightSpeed = MutableStateFlow(
        prefs.getFloat("long_press_right_speed", 2.0f).coerceIn(0.25f, 4.0f)
    )
    val longPressRightSpeed: StateFlow<Float> = _longPressRightSpeed.asStateFlow()

    fun setLongPressRightSpeed(speed: Float) {
        val bounded = (Math.round(speed.coerceIn(0.25f, 4.0f) * 100f) / 100f)
        _longPressRightSpeed.value = bounded
        prefs.edit().putFloat("long_press_right_speed", bounded).apply()
    }

    // Observe Room Database entities
    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarkedVideos: StateFlow<List<PlaylistItem>> = repository.playlists
        .flatMapLatest { list ->
            val bookmarks = list.firstOrNull { it.isSystem || it.title == "Bookmarks" }
            if (bookmarks != null) {
                repository.getItemsForPlaylist(bookmarks.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<VideoDownload>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

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

    fun playPrevious(currentPositionMs: Long, durationMs: Long) {
        val queue = _playbackQueue.value
        val currentIndex = _currentQueueIndex.value

        // If video progressed more than 10%, restart the current video
        if (durationMs > 0 && currentPositionMs > durationMs * 0.10) {
            restartCurrent()
            return
        }

        // Otherwise, go to previous video in queue
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

    fun setHardwareAcceleration(enabled: Boolean) {
        _hardwareAccelerationEnabled.value = enabled
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
            if (_selectedPlaylist.value?.id == id) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun updatePlaylistCustomThumbnail(playlistId: Long, customThumbnailPath: String?) {
        viewModelScope.launch {
            repository.updatePlaylistThumbnail(playlistId, customThumbnailPath)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = _selectedPlaylist.value?.copy(customThumbnailPath = customThumbnailPath)
            }
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

    fun addMultipleVideosToPlaylist(playlistId: Long, videos: List<VideoModel>) {
        viewModelScope.launch {
            videos.forEach { v ->
                repository.addVideoToPlaylist(playlistId, v.title, v.urlOrPath, v.subtitleUrlOrPath)
            }
        }
    }

    fun areAllVideosBookmarked(videos: List<VideoModel>): Boolean {
        if (videos.isEmpty()) return false
        val bookmarkedUrls = bookmarkedVideos.value.map { it.urlOrPath }.toSet()
        return videos.all { bookmarkedUrls.contains(it.urlOrPath) }
    }

    fun isVideoBookmarked(urlOrPath: String): Boolean {
        return bookmarkedVideos.value.any { it.urlOrPath == urlOrPath }
    }

    fun toggleBookmark(
        videos: List<VideoModel>,
        onResult: (isNowBookmarked: Boolean, count: Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (videos.isEmpty()) return@launch
            val allBookmarked = areAllVideosBookmarked(videos)
            if (allBookmarked) {
                val count = repository.removeVideosFromBookmarks(videos)
                onResult(false, count)
            } else {
                val count = repository.addVideosToBookmarks(videos)
                onResult(true, count)
            }
        }
    }

    fun addVideosToBookmarks(videos: List<VideoModel>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val addedCount = repository.addVideosToBookmarks(videos)
            onComplete(addedCount)
        }
    }

    fun removeVideosFromBookmarks(videos: List<VideoModel>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val removedCount = repository.removeVideosFromBookmarks(videos)
            onComplete(removedCount)
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

    fun renameVideo(context: Context, video: VideoModel, newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.renameVideoFile(context, video, newName)
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, res.getOrNull())
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteVideo(context: Context, video: VideoModel, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteVideoFile(context, video)
            if (res.isSuccess) {
                prefs.edit().remove("progress_${video.urlOrPath}").commit()
                scanLocalVideos(context)
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun copyVideo(context: Context, video: VideoModel, targetDirPath: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.copyVideoFile(context, video, targetDirPath)
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, res.getOrNull())
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun moveVideo(context: Context, video: VideoModel, targetDirPath: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.moveVideoFile(context, video, targetDirPath)
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, res.getOrNull())
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    // Real-Time File Operation Progress State
    private val _fileOperationState = MutableStateFlow<FileOperationState?>(null)
    val fileOperationState: StateFlow<FileOperationState?> = _fileOperationState.asStateFlow()

    fun copyVideoWithProgress(
        context: Context,
        video: VideoModel,
        targetDirPath: String,
        overwrite: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val opTitle = "Copying Video"
            _fileOperationState.value = FileOperationState(
                isRunning = true,
                title = opTitle,
                videoTitle = video.title,
                targetFolder = targetDirPath,
                progressRatio = 0f,
                bytesTransferred = 0L,
                totalBytes = video.size
            )

            val res = repository.copyVideoFileWithProgress(context, video, targetDirPath, overwrite) { transferred, total ->
                val ratio = if (total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                _fileOperationState.value = FileOperationState(
                    isRunning = true,
                    title = opTitle,
                    videoTitle = video.title,
                    targetFolder = targetDirPath,
                    progressRatio = ratio,
                    bytesTransferred = transferred,
                    totalBytes = total
                )
            }

            _fileOperationState.value = null
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, res.getOrNull())
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun moveVideoWithProgress(
        context: Context,
        video: VideoModel,
        targetDirPath: String,
        overwrite: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val opTitle = "Moving Video"
            _fileOperationState.value = FileOperationState(
                isRunning = true,
                title = opTitle,
                videoTitle = video.title,
                targetFolder = targetDirPath,
                progressRatio = 0f,
                bytesTransferred = 0L,
                totalBytes = video.size
            )

            val res = repository.moveVideoFileWithProgress(context, video, targetDirPath, overwrite) { transferred, total ->
                val ratio = if (total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                _fileOperationState.value = FileOperationState(
                    isRunning = true,
                    title = opTitle,
                    videoTitle = video.title,
                    targetFolder = targetDirPath,
                    progressRatio = ratio,
                    bytesTransferred = transferred,
                    totalBytes = total
                )
            }

            _fileOperationState.value = null
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, res.getOrNull())
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun copyMultipleVideosWithProgress(
        context: Context,
        videos: List<VideoModel>,
        targetDirPath: String,
        overwrite: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            if (videos.isEmpty()) {
                onResult(true, null)
                return@launch
            }

            val grandTotal = videos.fold(0L) { acc, v -> acc + File(v.urlOrPath).length() }
            val opTitle = "Copying ${videos.size} Videos"

            _fileOperationState.value = FileOperationState(
                isRunning = true,
                title = opTitle,
                videoTitle = videos.first().title,
                targetFolder = targetDirPath,
                progressRatio = 0f,
                bytesTransferred = 0L,
                totalBytes = grandTotal
            )

            val res = repository.copyMultipleVideosWithProgress(context, videos, targetDirPath, overwrite) { transferred, total, currFile ->
                val ratio = if (total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                _fileOperationState.value = FileOperationState(
                    isRunning = true,
                    title = opTitle,
                    videoTitle = currFile,
                    targetFolder = targetDirPath,
                    progressRatio = ratio,
                    bytesTransferred = transferred,
                    totalBytes = total
                )
            }

            _fileOperationState.value = null
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun moveMultipleVideosWithProgress(
        context: Context,
        videos: List<VideoModel>,
        targetDirPath: String,
        overwrite: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            if (videos.isEmpty()) {
                onResult(true, null)
                return@launch
            }

            val grandTotal = videos.fold(0L) { acc, v -> acc + File(v.urlOrPath).length() }
            val opTitle = "Moving ${videos.size} Videos"

            _fileOperationState.value = FileOperationState(
                isRunning = true,
                title = opTitle,
                videoTitle = videos.first().title,
                targetFolder = targetDirPath,
                progressRatio = 0f,
                bytesTransferred = 0L,
                totalBytes = grandTotal
            )

            val res = repository.moveMultipleVideosWithProgress(context, videos, targetDirPath, overwrite) { transferred, total, currFile ->
                val ratio = if (total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                _fileOperationState.value = FileOperationState(
                    isRunning = true,
                    title = opTitle,
                    videoTitle = currFile,
                    targetFolder = targetDirPath,
                    progressRatio = ratio,
                    bytesTransferred = transferred,
                    totalBytes = total
                )
            }

            _fileOperationState.value = null
            if (res.isSuccess) {
                scanLocalVideos(context)
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteMultipleVideos(
        context: Context,
        videos: List<VideoModel>,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            var successCount = 0
            for (v in videos) {
                val res = repository.deleteVideoFile(context, v)
                if (res.isSuccess) {
                    successCount++
                }
            }
            scanLocalVideos(context)
            if (successCount > 0) {
                onResult(true, null)
            } else {
                onResult(false, "Failed to delete files")
            }
        }
    }
}

data class FileOperationState(
    val isRunning: Boolean = false,
    val title: String = "",
    val videoTitle: String = "",
    val targetFolder: String = "",
    val progressRatio: Float = 0f,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L
)


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
