package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.VideoRepository
import com.example.viewmodel.VideoPlayerViewModel
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
        
        // Save progress at 50%
        viewModel.saveVideoProgress(videoUrl, 5000L, 10000L)
        
        val progressMap = viewModel.videoProgressMap.value
        assertNotNull(progressMap[videoUrl])
        assertEquals(5000L, progressMap[videoUrl]!!.first)
        assertEquals(10000L, progressMap[videoUrl]!!.second)

        // Save progress at 96% (watched whole)
        viewModel.saveVideoProgress(videoUrl, 9600L, 10000L)
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
    }
}
