package com.nativeplayer.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.nativeplayer.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    animationDurationMs: Long = 3900L,
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current

    // Build Coil ImageLoader with animated GIF support
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // Auto-advance after GIF animation completes
    LaunchedEffect(Unit) {
        delay(animationDurationMs)
        onSplashFinished()
    }

    // Intercept back button to skip splash screen if pressed
    BackHandler {
        onSplashFinished()
    }

    // Resolve splash background:
    // When in light theme, use Brand Dark Navy (#131321) so the white lettermark and vibrant colors
    // in the transparent GIF are crisp, punchy, and clearly readable with excellent contrast!
    val isLight = MaterialTheme.colorScheme.background.red > 0.4f && MaterialTheme.colorScheme.background.green > 0.4f
    val splashBgColor = if (isLight) Color(0xFF131321) else MaterialTheme.colorScheme.background

    // Full screen matching the contrast splash background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(splashBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Allow tapping anywhere to skip splash
                onSplashFinished()
            }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.drawable.splash_logo_1)
                .crossfade(false)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "App Splash Animation",
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .aspectRatio(1f),
            contentScale = ContentScale.Fit
        )
    }
}
