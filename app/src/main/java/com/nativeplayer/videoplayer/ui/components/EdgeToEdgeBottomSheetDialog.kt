package com.nativeplayer.videoplayer.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

private data class SystemBarsInsets(
    val navBottom: Dp = 0.dp,
    val navStart: Dp = 0.dp,
    val navEnd: Dp = 0.dp,
    val statusTop: Dp = 0.dp
)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun getSystemBarsInsets(
    context: Context,
    density: Density,
    layoutDirection: LayoutDirection
): SystemBarsInsets {
    val activity = context.findActivity()
    val decorView = activity?.window?.decorView
    if (decorView != null) {
        val rootInsets = ViewCompat.getRootWindowInsets(decorView)
        var navBottomPx = 0
        var navStartPx = 0
        var navEndPx = 0
        var statusTopPx = 0

        if (rootInsets != null) {
            val combinedTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.mandatorySystemGestures() or
                    WindowInsetsCompat.Type.tappableElement()
            val insets = rootInsets.getInsets(combinedTypes)
            navBottomPx = insets.bottom
            val isRtl = layoutDirection == LayoutDirection.Rtl
            navStartPx = if (isRtl) insets.right else insets.left
            navEndPx = if (isRtl) insets.left else insets.right
            statusTopPx = rootInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        }

        // On API 30+ (Android 11 - 16+), query platform WindowInsets with getInsetsIgnoringVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val platformInsets = decorView.rootWindowInsets
            if (platformInsets != null) {
                val navIgnored = platformInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                val sysIgnored = platformInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
                val navVisible = platformInsets.getInsets(android.view.WindowInsets.Type.navigationBars())
                val sysVisible = platformInsets.getInsets(android.view.WindowInsets.Type.systemBars())
                val gestures = platformInsets.getInsets(android.view.WindowInsets.Type.mandatorySystemGestures())
                val tappable = platformInsets.getInsets(android.view.WindowInsets.Type.tappableElement())

                navBottomPx = maxOf(
                    navBottomPx,
                    navIgnored.bottom,
                    sysIgnored.bottom,
                    navVisible.bottom,
                    sysVisible.bottom,
                    gestures.bottom,
                    tappable.bottom
                )
                val isRtl = layoutDirection == LayoutDirection.Rtl
                val leftMax = maxOf(navIgnored.left, sysIgnored.left, navVisible.left, sysVisible.left)
                val rightMax = maxOf(navIgnored.right, sysIgnored.right, navVisible.right, sysVisible.right)
                navStartPx = maxOf(navStartPx, if (isRtl) rightMax else leftMax)
                navEndPx = maxOf(navEndPx, if (isRtl) leftMax else rightMax)
                statusTopPx = maxOf(statusTopPx, sysIgnored.top, sysVisible.top)
            }
        }

        if (navBottomPx > 0 || statusTopPx > 0) {
            return with(density) {
                SystemBarsInsets(
                    navBottom = navBottomPx.toDp(),
                    navStart = navStartPx.toDp(),
                    navEnd = navEndPx.toDp(),
                    statusTop = statusTopPx.toDp()
                )
            }
        }
    }
    // Fallback: Read framework dimen if rootInsets is null or returned 0
    return try {
        val res = context.resources
        val navResId = res.getIdentifier("navigation_bar_height", "dimen", "android")
        val statusResId = res.getIdentifier("status_bar_height", "dimen", "android")
        val navHeight = if (navResId > 0) with(density) { res.getDimensionPixelSize(navResId).toDp() } else 0.dp
        val statusHeight = if (statusResId > 0) with(density) { res.getDimensionPixelSize(statusResId).toDp() } else 0.dp
        SystemBarsInsets(
            navBottom = navHeight,
            statusTop = statusHeight
        )
    } catch (_: Exception) {
        SystemBarsInsets()
    }
}

/**
 * A bottom sheet styled Dialog that provides complete Edge-to-Edge display support across Android 13 - 16+:
 * 1. Resolves true system navigation and status bar insets from the host Activity before entering Dialog subcomposition.
 * 2. Reads live OS insets from DecorView and system dimension resources so insets are never missed on Android 15/16.
 * 3. Listens to dialog window insets directly via OnApplyWindowInsetsListener and getInsetsIgnoringVisibility.
 * 4. Enforces an absolute safety floor of 48.dp on Android 15/16 (API 35+) where edge-to-edge is mandatory,
 *    guaranteeing bottom buttons/content sit cleanly above any 3-button or gesture navigation bar.
 * 5. Configures Dialog window to MATCH_PARENT width and height with Gravity.BOTTOM and transparent system bars.
 * 6. Floats interactive contents cleanly above the navigation bar with proper insets padding while drawing Surface behind.
 */
@Composable
fun EdgeToEdgeBottomSheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    topCornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // ── 1. Read insets from the parent (Activity) before entering Dialog subcomposition ──
    val activityNavPadding = WindowInsets.navigationBars.asPaddingValues()
    val activityNavBottom = activityNavPadding.calculateBottomPadding()
    val activityNavStart = activityNavPadding.calculateStartPadding(layoutDirection)
    val activityNavEnd = activityNavPadding.calculateEndPadding(layoutDirection)

    val activityStatusPadding = WindowInsets.statusBars.asPaddingValues()
    val activityStatusTop = activityStatusPadding.calculateTopPadding()

    var systemInsets by remember {
        mutableStateOf(getSystemBarsInsets(context, density, layoutDirection))
    }

    DisposableEffect(context, density, layoutDirection) {
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            systemInsets = getSystemBarsInsets(context, density, layoutDirection)
        }
        decorView?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        onDispose {
            decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
    }

    var dialogNavBottom by remember { mutableStateOf(0.dp) }
    var dialogNavStart by remember { mutableStateOf(0.dp) }
    var dialogNavEnd by remember { mutableStateOf(0.dp) }

    // Dynamic resolution taking the highest accurate insets:
    // On Android 15/16 (API 35+), enforce at least 48.dp floor (standard 3-button navigation height)
    // to guarantee bottom buttons/content are never submerged under the navigation bar.
    val minNavBottomFloor = if (Build.VERSION.SDK_INT >= 35) 48.dp else 0.dp
    val effectiveNavBottom = maxOf(
        activityNavBottom,
        systemInsets.navBottom,
        dialogNavBottom,
        minNavBottomFloor
    )
    val effectiveNavStart = maxOf(activityNavStart, systemInsets.navStart, dialogNavStart)
    val effectiveNavEnd = maxOf(activityNavEnd, systemInsets.navEnd, dialogNavEnd)
    val effectiveStatusTop = maxOf(activityStatusTop, systemInsets.statusTop, 24.dp)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val view = LocalView.current

        DisposableEffect(view) {
            val window = findDialogWindow(view)
            window?.let { w ->
                w.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                w.setGravity(Gravity.BOTTOM)
                w.setBackgroundDrawableResource(android.R.color.transparent)
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    w.isNavigationBarContrastEnforced = false
                    w.isStatusBarContrastEnforced = false
                }

                fun inspectViewInsets(targetView: View) {
                    val insetsCompat = ViewCompat.getRootWindowInsets(targetView)
                    if (insetsCompat != null) {
                        val combined = WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.navigationBars() or
                                WindowInsetsCompat.Type.mandatorySystemGestures() or
                                WindowInsetsCompat.Type.tappableElement()
                        val nav = insetsCompat.getInsets(combined)
                        if (nav.bottom > 0) {
                            with(density) { dialogNavBottom = maxOf(dialogNavBottom, nav.bottom.toDp()) }
                        }
                        val isRtl = layoutDirection == LayoutDirection.Rtl
                        val sPx = if (isRtl) nav.right else nav.left
                        val ePx = if (isRtl) nav.left else nav.right
                        if (sPx > 0) with(density) { dialogNavStart = maxOf(dialogNavStart, sPx.toDp()) }
                        if (ePx > 0) with(density) { dialogNavEnd = maxOf(dialogNavEnd, ePx.toDp()) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val root = targetView.rootWindowInsets
                        if (root != null) {
                            val navIgnored = root.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                            val sysIgnored = root.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
                            val navVis = root.getInsets(android.view.WindowInsets.Type.navigationBars())
                            val sysVis = root.getInsets(android.view.WindowInsets.Type.systemBars())
                            val gestures = root.getInsets(android.view.WindowInsets.Type.mandatorySystemGestures())
                            val tappable = root.getInsets(android.view.WindowInsets.Type.tappableElement())
                            val maxB = maxOf(navIgnored.bottom, sysIgnored.bottom, navVis.bottom, sysVis.bottom, gestures.bottom, tappable.bottom)
                            if (maxB > 0) {
                                with(density) { dialogNavBottom = maxOf(dialogNavBottom, maxB.toDp()) }
                            }
                            val isRtl = layoutDirection == LayoutDirection.Rtl
                            val maxL = maxOf(navIgnored.left, sysIgnored.left, navVis.left, sysVis.left)
                            val maxR = maxOf(navIgnored.right, sysIgnored.right, navVis.right, sysVis.right)
                            val sPx = if (isRtl) maxR else maxL
                            val ePx = if (isRtl) maxL else maxR
                            if (sPx > 0) with(density) { dialogNavStart = maxOf(dialogNavStart, sPx.toDp()) }
                            if (ePx > 0) with(density) { dialogNavEnd = maxOf(dialogNavEnd, ePx.toDp()) }
                        }
                    }
                }

                inspectViewInsets(w.decorView)
                inspectViewInsets(view)

                ViewCompat.setOnApplyWindowInsetsListener(w.decorView) { v, insets ->
                    inspectViewInsets(v)
                    insets
                }
                ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                    inspectViewInsets(v)
                    insets
                }
            }
            onDispose { }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = effectiveStatusTop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {},
                shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = effectiveNavStart,
                            end = effectiveNavEnd,
                            bottom = effectiveNavBottom
                        )
                        .padding(contentPadding)
                        .then(contentModifier)
                ) {
                    content()
                }
            }
        }
    }
}

private fun findDialogWindow(view: View): Window? {
    var current: ViewParent? = view.parent
    while (current != null) {
        if (current is DialogWindowProvider) {
            return current.window
        }
        current = current.parent
    }
    return (view as? DialogWindowProvider)?.window
}
