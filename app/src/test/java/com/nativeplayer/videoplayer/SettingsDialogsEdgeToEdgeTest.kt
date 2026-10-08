package com.nativeplayer.videoplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nativeplayer.videoplayer.data.AppDatabase
import com.nativeplayer.videoplayer.data.VideoRepository
import com.nativeplayer.videoplayer.viewmodel.AppFontSize
import com.nativeplayer.videoplayer.viewmodel.AppTheme
import com.nativeplayer.videoplayer.viewmodel.PlaylistThumbnailPattern
import com.nativeplayer.videoplayer.viewmodel.ScreenshotLocation
import com.nativeplayer.videoplayer.viewmodel.SubtitleStyle
import com.nativeplayer.videoplayer.viewmodel.ThumbnailMode
import com.nativeplayer.videoplayer.viewmodel.VideoPlayerViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDialogsEdgeToEdgeTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: VideoRepository
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VideoRepository(database.videoPlayerDao())
        viewModel = VideoPlayerViewModel(repository, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppThemeDialogSelection() {
        // Default theme
        assertEquals(AppTheme.System, viewModel.appTheme.value)

        // Select Dark
        viewModel.setAppTheme(AppTheme.Dark)
        assertEquals(AppTheme.Dark, viewModel.appTheme.value)

        // Select Light
        viewModel.setAppTheme(AppTheme.Light)
        assertEquals(AppTheme.Light, viewModel.appTheme.value)

        // Select Custom
        viewModel.setAppTheme(AppTheme.Custom)
        assertEquals(AppTheme.Custom, viewModel.appTheme.value)

        // Select System
        viewModel.setAppTheme(AppTheme.System)
        assertEquals(AppTheme.System, viewModel.appTheme.value)
    }

    @Test
    fun testAppFontSizeDialogSelection() {
        // Default font size
        assertEquals(AppFontSize.Regular, viewModel.appFontSize.value)

        // Test each selectable font size from the dialog
        AppFontSize.values().forEach { fontSize ->
            viewModel.setAppFontSize(fontSize)
            assertEquals(fontSize, viewModel.appFontSize.value)
        }
    }

    @Test
    fun testButtonSeekIntervalDialogSelection() {
        // Default seek interval
        assertEquals(10, viewModel.buttonSeekSeconds.value)

        // Update seek intervals available in dialog: 5, 15, 30, 60
        viewModel.setButtonSeekSeconds(5)
        assertEquals(5, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(15)
        assertEquals(15, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(30)
        assertEquals(30, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(60)
        assertEquals(60, viewModel.buttonSeekSeconds.value)
    }

    @Test
    fun testSubtitleStyleDialogSelection() {
        // Default subtitle style
        assertEquals(SubtitleStyle.Default, viewModel.subtitleStyle.value)

        // Test all styles selectable from the dialog
        SubtitleStyle.values().forEach { style ->
            viewModel.setSubtitleStyle(style)
            assertEquals(style, viewModel.subtitleStyle.value)
        }
    }

    @Test
    fun testThumbnailModeDialogSelection() {
        // Default thumbnail mode
        assertEquals(ThumbnailMode.PERCENTAGE, viewModel.thumbnailMode.value)

        ThumbnailMode.values().forEach { mode ->
            viewModel.setThumbnailMode(mode)
            assertEquals(mode, viewModel.thumbnailMode.value)
        }

        // Test thumbnail percentage slider value
        viewModel.setThumbnailPercentage(25)
        assertEquals(25, viewModel.thumbnailPercentage.value)

        viewModel.setThumbnailPercentage(50)
        assertEquals(50, viewModel.thumbnailPercentage.value)
    }

    @Test
    fun testScreenshotLocationDialogSelection() {
        // Default screenshot location
        assertEquals(ScreenshotLocation.APP_FOLDER, viewModel.screenshotLocation.value)

        ScreenshotLocation.values().forEach { loc ->
            viewModel.setScreenshotLocation(loc)
            assertEquals(loc, viewModel.screenshotLocation.value)
        }
    }

    @Test
    fun testPlaylistThumbnailPatternDialogSelection() {
        // Default pattern
        assertEquals(PlaylistThumbnailPattern.FIRST_VIDEO, viewModel.playlistThumbnailPattern.value)

        viewModel.setPlaylistThumbnailPattern(PlaylistThumbnailPattern.LAST_VIDEO)
        assertEquals(PlaylistThumbnailPattern.LAST_VIDEO, viewModel.playlistThumbnailPattern.value)
    }

    @Test
    fun testDisplaySettingsDialogState() {
        // Verify display settings dialog visibility state toggle
        assertFalse(viewModel.showDisplaySettingsDialog.value)

        viewModel.setShowDisplaySettingsDialog(true)
        assertTrue(viewModel.showDisplaySettingsDialog.value)

        viewModel.setShowDisplaySettingsDialog(false)
        assertFalse(viewModel.showDisplaySettingsDialog.value)
    }
}
