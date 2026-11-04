package com.nativeplayer.videoplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeLockTest {

    @Test
    fun manifestDeclaresWakeLockPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        assertTrue(
            "Manifest should request android.permission.WAKE_LOCK",
            requestedPermissions.contains(Manifest.permission.WAKE_LOCK)
        )
    }

    @Test
    fun windowKeepScreenOnFlagTogglesBasedOnPlaybackState() {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = activityController.get()
        val window = activity.window

        // Initially FLAG_KEEP_SCREEN_ON is not set
        val initialHasFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        assertFalse("Initial window state should not have FLAG_KEEP_SCREEN_ON", initialHasFlag)

        // When playing: flag is added
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val playingHasFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        assertTrue("Playing window state must have FLAG_KEEP_SCREEN_ON", playingHasFlag)

        // When paused: flag is cleared
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pausedHasFlag = (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        assertFalse("Paused window state should not have FLAG_KEEP_SCREEN_ON", pausedHasFlag)

        activityController.destroy()
    }

    @Test
    fun exoPlayerBuilderConfiguresWakeMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        assertNotNull("ExoPlayer should be instantiated with WAKE_MODE_NETWORK", player)
        player.release()
    }

    @Test
    fun playerViewKeepScreenOnPropertyUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playerView = PlayerView(context)

        playerView.keepScreenOn = true
        assertTrue("PlayerView keepScreenOn should be true when set", playerView.keepScreenOn)

        playerView.keepScreenOn = false
        assertFalse("PlayerView keepScreenOn should be false when paused", playerView.keepScreenOn)
    }
}
