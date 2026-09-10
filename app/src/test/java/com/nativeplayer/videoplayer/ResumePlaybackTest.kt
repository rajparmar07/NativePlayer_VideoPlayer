package com.nativeplayer.videoplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nativeplayer.videoplayer.data.AppDatabase
import com.nativeplayer.videoplayer.data.VideoRepository
import com.nativeplayer.videoplayer.viewmodel.VideoPlayerViewModel
import com.nativeplayer.videoplayer.viewmodel.ScreenshotLocation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumePlaybackTest {

    private lateinit var context: Context
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val database = AppDatabase.getDatabase(context)
        val repository = VideoRepository(database.videoPlayerDao())
        viewModel = VideoPlayerViewModel(repository, context)
        
        // Clear preferences before each test to ensure a clean state
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testResumePlaybackSettingToggle() {
        // Default is true
        assertTrue(viewModel.resumeFromLastLeftEnabled.value)

        // Disable setting
        viewModel.setResumeFromLastLeftEnabled(false)
        assertFalse(viewModel.resumeFromLastLeftEnabled.value)

        // Enable setting again
        viewModel.setResumeFromLastLeftEnabled(true)
        assertTrue(viewModel.resumeFromLastLeftEnabled.value)
    }

    @Test
    fun testSaveVideoProgress() {
        val videoUrl = "https://example.com/video.mp4"
        
        // Save progress at 50% (5000ms / 10000ms = 50% -> in 3%..97% and >= 2000ms -> saved)
        viewModel.saveVideoProgress(videoUrl, 5000L, 10000L)
        
        val progressMap = viewModel.videoProgressMap.value
        assertNotNull(progressMap[videoUrl])
        assertEquals(5000L, progressMap[videoUrl]!!.first)
        assertEquals(10000L, progressMap[videoUrl]!!.second)

        // Save progress at 98% (9800ms / 10000ms = 98% -> > 97% -> removed / not saved)
        viewModel.saveVideoProgress(videoUrl, 9800L, 10000L)
        assertNull(viewModel.videoProgressMap.value[videoUrl])

        // Save progress at 2% (2000ms / 100000ms = 2% -> < 3% -> removed / not saved)
        viewModel.saveVideoProgress(videoUrl, 2000L, 100000L)
        assertNull(viewModel.videoProgressMap.value[videoUrl])

        // Save progress at 5% (5000ms / 100000ms = 5% -> in 3%..97% and >= 2000ms -> saved)
        viewModel.saveVideoProgress(videoUrl, 5000L, 100000L)
        assertNotNull(viewModel.videoProgressMap.value[videoUrl])
        assertEquals(5000L, viewModel.videoProgressMap.value[videoUrl]!!.first)

        // Save progress < 2000ms (1500ms / 10000ms = 15% -> progress < 2000ms -> removed / not saved)
        viewModel.saveVideoProgress(videoUrl, 1500L, 10000L)
        assertNull(viewModel.videoProgressMap.value[videoUrl])
    }

    @Test
    fun testPlayerControllerTimeoutSetting() {
        // Default is 4 seconds
        assertEquals(4, viewModel.playerControllerTimeout.value)

        // Set custom valid timeout
        viewModel.setPlayerControllerTimeout(10)
        assertEquals(10, viewModel.playerControllerTimeout.value)

        // Test coercion (min value boundary)
        viewModel.setPlayerControllerTimeout(0)
        assertEquals(1, viewModel.playerControllerTimeout.value)

        // Test coercion (max value boundary)
        viewModel.setPlayerControllerTimeout(100)
        assertEquals(60, viewModel.playerControllerTimeout.value)
    }

    @Test
    fun testPictureInPictureSettings() {
        // Default is true
        assertTrue(viewModel.pipEnabled.value)

        // Toggle to false
        viewModel.setPipEnabled(false)
        assertFalse(viewModel.pipEnabled.value)

        // Default isInPipMode is false
        assertFalse(viewModel.isInPipMode.value)
        viewModel.setInPipMode(true)
        assertTrue(viewModel.isInPipMode.value)

        // Default dimensions
        assertEquals(16, viewModel.videoWidth.value)
        assertEquals(9, viewModel.videoHeight.value)
        viewModel.setVideoDimensions(4, 3)
        assertEquals(4, viewModel.videoWidth.value)
        assertEquals(3, viewModel.videoHeight.value)
    }

    @Test
    fun testGestureSettings() {
        // Defaults
        assertTrue(viewModel.seekGestureEnabled.value)
        assertTrue(viewModel.volumeGestureEnabled.value)
        assertTrue(viewModel.brightnessGestureEnabled.value)
        assertEquals(60, viewModel.seekSwipePixels.value)
        assertEquals(1000, viewModel.volumeSwipePixels.value)
        assertEquals(0.0015f, viewModel.brightnessSensitivity.value)

        // Disable/enable switches
        viewModel.setSeekGestureEnabled(false)
        assertFalse(viewModel.seekGestureEnabled.value)
        viewModel.setVolumeGestureEnabled(false)
        assertFalse(viewModel.volumeGestureEnabled.value)
        viewModel.setBrightnessGestureEnabled(false)
        assertFalse(viewModel.brightnessGestureEnabled.value)

        // Test sensitivity values and bounds coercion
        viewModel.setSeekSwipePixels(45)
        assertEquals(45, viewModel.seekSwipePixels.value)
        viewModel.setSeekSwipePixels(10) // below min (20)
        assertEquals(20, viewModel.seekSwipePixels.value)
        viewModel.setSeekSwipePixels(200) // above max (150)
        assertEquals(150, viewModel.seekSwipePixels.value)

        viewModel.setVolumeSwipePixels(1500)
        assertEquals(1500, viewModel.volumeSwipePixels.value)
        viewModel.setVolumeSwipePixels(50) // below min (200)
        assertEquals(200, viewModel.volumeSwipePixels.value)
        viewModel.setVolumeSwipePixels(3000) // above max (2000)
        assertEquals(2000, viewModel.volumeSwipePixels.value)

        viewModel.setBrightnessSensitivity(0.0035f)
        assertEquals(0.0035f, viewModel.brightnessSensitivity.value)
        viewModel.setBrightnessSensitivity(0.0001f) // below min (0.0005f)
        assertEquals(0.0005f, viewModel.brightnessSensitivity.value)
        viewModel.setBrightnessSensitivity(0.0080f) // above max (0.0050f)
        assertEquals(0.0050f, viewModel.brightnessSensitivity.value)

        // Test zoom settings
        assertTrue(viewModel.zoomGestureEnabled.value)
        assertEquals(1.0f, viewModel.zoomSensitivity.value)

        viewModel.setZoomGestureEnabled(false)
        assertFalse(viewModel.zoomGestureEnabled.value)

        viewModel.setZoomSensitivity(1.5f)
        assertEquals(1.5f, viewModel.zoomSensitivity.value)
        viewModel.setZoomSensitivity(0.1f) // below min (0.5f)
        assertEquals(0.5f, viewModel.zoomSensitivity.value)
        viewModel.setZoomSensitivity(3.0f) // above max (2.0f)
        assertEquals(2.0f, viewModel.zoomSensitivity.value)

        // Test long press speed gesture settings
        assertTrue(viewModel.longPressSpeedEnabled.value)
        assertEquals(com.nativeplayer.videoplayer.viewmodel.LongPressMode.WHOLE_SCREEN, viewModel.longPressMode.value)
        assertEquals(2.0f, viewModel.longPressWholeScreenSpeed.value, 0.01f)
        assertEquals(2.0f, viewModel.longPressLeftSpeed.value, 0.01f)
        assertEquals(2.0f, viewModel.longPressRightSpeed.value, 0.01f)

        viewModel.setLongPressSpeedEnabled(false)
        assertFalse(viewModel.longPressSpeedEnabled.value)

        viewModel.setLongPressMode(com.nativeplayer.videoplayer.viewmodel.LongPressMode.SPLIT_SCREEN)
        assertEquals(com.nativeplayer.videoplayer.viewmodel.LongPressMode.SPLIT_SCREEN, viewModel.longPressMode.value)

        viewModel.setLongPressWholeScreenSpeed(3.5f)
        assertEquals(3.5f, viewModel.longPressWholeScreenSpeed.value, 0.01f)
        viewModel.setLongPressWholeScreenSpeed(0.1f) // below min (0.25f)
        assertEquals(0.25f, viewModel.longPressWholeScreenSpeed.value, 0.01f)
        viewModel.setLongPressWholeScreenSpeed(5.0f) // above max (4.0f)
        assertEquals(4.0f, viewModel.longPressWholeScreenSpeed.value, 0.01f)

        viewModel.setLongPressLeftSpeed(1.5f)
        assertEquals(1.5f, viewModel.longPressLeftSpeed.value, 0.01f)
        viewModel.setLongPressLeftSpeed(0.1f) // below min (0.25f)
        assertEquals(0.25f, viewModel.longPressLeftSpeed.value, 0.01f)

        viewModel.setLongPressRightSpeed(2.5f)
        assertEquals(2.5f, viewModel.longPressRightSpeed.value, 0.01f)
        viewModel.setLongPressRightSpeed(6.0f) // above max (4.0f)
        assertEquals(4.0f, viewModel.longPressRightSpeed.value, 0.01f)
    }

    @Test
    fun testSaveAndRestoreTrackPreferences() {
        val videoUrl = "https://example.com/test_video_tracks.mp4"

        // Save progress with audio and subtitle track selections
        viewModel.saveVideoProgress(
            urlOrPath = videoUrl,
            progressMs = 12000L,
            durationMs = 60000L,
            audioGroupIndex = 1,
            audioTrackIndex = 0,
            audioLanguage = "eng",
            subtitleGroupIndex = 2,
            subtitleTrackIndex = 1,
            subtitleLanguage = "spa",
            isSubtitleDisabled = false
        )

        val state = viewModel.getVideoPlaybackState(videoUrl)
        assertNotNull(state)
        assertEquals(12000L, state!!.progressMs)
        assertEquals(60000L, state.durationMs)
        assertEquals(1, state.audioGroupIndex)
        assertEquals(0, state.audioTrackIndex)
        assertEquals("eng", state.audioLanguage)
        assertEquals(2, state.subtitleGroupIndex)
        assertEquals(1, state.subtitleTrackIndex)
        assertEquals("spa", state.subtitleLanguage)
        assertFalse(state.isSubtitleDisabled)
    }

    @Test
    fun testAudioSettings() {
        // Defaults
        assertTrue(viewModel.audioFocusEnabled.value)
        assertTrue(viewModel.pauseOnHeadphonesDisconnectEnabled.value)

        // Toggle Audio Focus setting
        viewModel.setAudioFocusEnabled(false)
        assertFalse(viewModel.audioFocusEnabled.value)

        viewModel.setAudioFocusEnabled(true)
        assertTrue(viewModel.audioFocusEnabled.value)

        // Toggle Pause on Headset Disconnect setting
        viewModel.setPauseOnHeadphonesDisconnectEnabled(false)
        assertFalse(viewModel.pauseOnHeadphonesDisconnectEnabled.value)

        viewModel.setPauseOnHeadphonesDisconnectEnabled(true)
        assertTrue(viewModel.pauseOnHeadphonesDisconnectEnabled.value)
    }

    @Test
    fun testButtonSeekSeconds() {
        // Default value
        assertEquals(10, viewModel.buttonSeekSeconds.value)

        // Set valid values
        viewModel.setButtonSeekSeconds(5)
        assertEquals(5, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(15)
        assertEquals(15, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(30)
        assertEquals(30, viewModel.buttonSeekSeconds.value)

        viewModel.setButtonSeekSeconds(60)
        assertEquals(60, viewModel.buttonSeekSeconds.value)

        // Invalid fallback defaults to 10
        viewModel.setButtonSeekSeconds(22)
        assertEquals(10, viewModel.buttonSeekSeconds.value)
    }

    @Test
    fun testScreenshotLocationSetting() {
        // Default is APP_FOLDER
        assertEquals(ScreenshotLocation.APP_FOLDER, viewModel.screenshotLocation.value)

        // Switch to SCREENSHOTS
        viewModel.setScreenshotLocation(ScreenshotLocation.SCREENSHOTS)
        assertEquals(ScreenshotLocation.SCREENSHOTS, viewModel.screenshotLocation.value)

        // Switch back to APP_FOLDER
        viewModel.setScreenshotLocation(ScreenshotLocation.APP_FOLDER)
        assertEquals(ScreenshotLocation.APP_FOLDER, viewModel.screenshotLocation.value)
    }
}
