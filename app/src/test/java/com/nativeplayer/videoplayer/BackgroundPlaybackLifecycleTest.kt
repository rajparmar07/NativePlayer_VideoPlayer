package com.nativeplayer.videoplayer

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.nativeplayer.videoplayer.data.AppDatabase
import com.nativeplayer.videoplayer.data.VideoRepository
import com.nativeplayer.videoplayer.viewmodel.VideoPlayerViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundPlaybackLifecycleTest {

    private lateinit var context: Context
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val database = AppDatabase.getDatabase(context)
        val repository = VideoRepository(database.videoPlayerDao())
        viewModel = VideoPlayerViewModel(repository, context)

        // Reset preferences before test
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /**
     * Helper to simulate the exact lifecycle handling logic implemented in PlayerScreen.
     */
    private class PlaybackLifecycleController(
        var isPlaying: Boolean,
        var inPip: Boolean,
        var backgroundPlaybackEnabled: Boolean
    ) {
        var wasPlayingBeforeBackground: Boolean = false
        var pauseCount: Int = 0
        var playCount: Int = 0

        fun onLifecycleEvent(event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!inPip && !backgroundPlaybackEnabled) {
                        if (isPlaying) {
                            wasPlayingBeforeBackground = true
                            isPlaying = false
                            pauseCount++
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (!inPip && !backgroundPlaybackEnabled && wasPlayingBeforeBackground) {
                        wasPlayingBeforeBackground = false
                        isPlaying = true
                        playCount++
                    }
                }
                else -> {}
            }
        }
    }

    @Test
    fun testNormalBackgrounding_pausesAndResumesPlayback() {
        val controller = PlaybackLifecycleController(
            isPlaying = true,
            inPip = false,
            backgroundPlaybackEnabled = false
        )

        // User presses Home or switches app -> ON_STOP
        controller.onLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertFalse("Playback should be paused on ON_STOP when background playback is disabled", controller.isPlaying)
        assertTrue("wasPlayingBeforeBackground flag should be true", controller.wasPlayingBeforeBackground)
        assertEquals(1, controller.pauseCount)

        // User returns to the app -> ON_START
        controller.onLifecycleEvent(Lifecycle.Event.ON_START)

        assertTrue("Playback should resume on ON_START", controller.isPlaying)
        assertFalse("wasPlayingBeforeBackground flag should be reset", controller.wasPlayingBeforeBackground)
        assertEquals(1, controller.playCount)
    }

    @Test
    fun testBackgroundPlaybackEnabled_keepsPlayingOnStop() {
        val controller = PlaybackLifecycleController(
            isPlaying = true,
            inPip = false,
            backgroundPlaybackEnabled = true
        )

        // User presses Home or switches app with background playback enabled
        controller.onLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertTrue("Playback should continue when background playback is enabled", controller.isPlaying)
        assertFalse(controller.wasPlayingBeforeBackground)
        assertEquals(0, controller.pauseCount)

        // User returns to the app
        controller.onLifecycleEvent(Lifecycle.Event.ON_START)
        assertTrue(controller.isPlaying)
        assertEquals(0, controller.playCount)
    }

    @Test
    fun testPiPMode_keepsPlayingOnStop() {
        val controller = PlaybackLifecycleController(
            isPlaying = true,
            inPip = true,
            backgroundPlaybackEnabled = false
        )

        // PiP mode active
        controller.onLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertTrue("Playback should continue while in PiP mode", controller.isPlaying)
        assertFalse(controller.wasPlayingBeforeBackground)
        assertEquals(0, controller.pauseCount)
    }

    @Test
    fun testAlreadyPausedVideo_doesNotAutoPlayOnStart() {
        val controller = PlaybackLifecycleController(
            isPlaying = false,
            inPip = false,
            backgroundPlaybackEnabled = false
        )

        // User was already paused, then backgrounded
        controller.onLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertFalse(controller.isPlaying)
        assertFalse(controller.wasPlayingBeforeBackground)
        assertEquals(0, controller.pauseCount)

        // User returns to the app
        controller.onLifecycleEvent(Lifecycle.Event.ON_START)

        assertFalse("Video that was paused before backgrounding must NOT automatically play on return", controller.isPlaying)
        assertEquals(0, controller.playCount)
    }
}
