package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ThumbnailCache
import com.example.ui.components.ThumbnailDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data model ────────────────────────────────────────────────────────────────

private data class MemoryStats(
    val thumbnailBytes: Long,
    val thumbnailCount: Int,
    val databaseBytes: Long,
    val downloadedFilesBytes: Long
) {
    val totalBytes: Long get() = thumbnailBytes + databaseBytes + downloadedFilesBytes
}


private suspend fun calculateStats(context: Context): MemoryStats = withContext(Dispatchers.IO) {
    val thumbnailBytes = ThumbnailDiskCache.getCacheSizeBytes(context)
    val thumbnailCount = ThumbnailDiskCache.getThumbnailCount(context)
    val dbFile = context.getDatabasePath("video_player.db")
    val databaseBytes = if (dbFile != null && dbFile.exists()) dbFile.length() else 0L
    val downloadedBytes = context.filesDir
        .walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }
    MemoryStats(thumbnailBytes, thumbnailCount, databaseBytes, downloadedBytes)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val snackbar   = remember { SnackbarHostState() }

    var stats         by remember { mutableStateOf<MemoryStats?>(null) }
    var isLoading     by remember { mutableStateOf(true) }
    var isClearing    by remember { mutableStateOf(false) }
    var showClearDlg  by remember { mutableStateOf(false) }

    // Segment colours
    val thumbColor = Color(0xFFFFB74D)   // amber
    val dbColor    = Color(0xFF4FC3F7)   // sky-blue
    val dlColor    = Color(0xFF81C784)   // green

    LaunchedEffect(Unit) {
        isLoading = true
        stats     = calculateStats(context)
        isLoading = false
    }

    Scaffold(
        topBar = {
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                color          = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text       = "Memory & Cache",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector     = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor          = MaterialTheme.colorScheme.surface,
                        titleContentColor       = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        snackbarHost   = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->

        // ── Loading state ────────────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier           = Modifier.fillMaxSize().padding(pad),
                contentAlignment   = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text  = "Calculating storage…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        // ── Content ──────────────────────────────────────────────────────────
        val s = stats ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Overview card ────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text       = "Storage Overview",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = MaterialTheme.colorScheme.onSurface
                    )

                    // Hero total
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text       = formatSize(s.totalBytes),
                            fontSize   = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text     = "total used",
                            fontSize = 13.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }

                    // Animated segmented bar
                    if (s.totalBytes > 0L) {
                        val total = s.totalBytes.toFloat()
                        val animThumb by animateFloatAsState(
                            targetValue = (s.thumbnailBytes / total).coerceIn(0f, 1f),
                            animationSpec = tween(900), label = "thumb"
                        )
                        val animDb by animateFloatAsState(
                            targetValue = (s.databaseBytes / total).coerceIn(0f, 1f),
                            animationSpec = tween(900), label = "db"
                        )
                        val animDl by animateFloatAsState(
                            targetValue = (s.downloadedFilesBytes / total).coerceIn(0f, 1f),
                            animationSpec = tween(900), label = "dl"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(50))
                        ) {
                            if (animThumb > 0f) Box(Modifier.weight(animThumb.coerceAtLeast(0.005f)).fillMaxHeight().background(thumbColor))
                            if (animDb > 0f)    Box(Modifier.weight(animDb.coerceAtLeast(0.005f)).fillMaxHeight().background(dbColor))
                            if (animDl > 0f)    Box(Modifier.weight(animDl.coerceAtLeast(0.005f)).fillMaxHeight().background(dlColor))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }

                    // Legend
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StorageLegendDot(color = thumbColor, label = "Thumbnails")
                        StorageLegendDot(color = dbColor,    label = "Database")
                        StorageLegendDot(color = dlColor,    label = "Downloads")
                    }
                }
            }

            // ── Breakdown section ────────────────────────────────────────────
            Text(
                text       = "Breakdown",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.padding(top = 6.dp, start = 4.dp)
            )

            StorageItemCard(
                icon      = Icons.Default.Image,
                iconTint  = thumbColor,
                iconBg    = thumbColor.copy(alpha = 0.15f),
                title     = "Thumbnail Cache",
                subtitle  = "${formatSize(s.thumbnailBytes)} · ${s.thumbnailCount} cached thumbnails",
                badge     = "Clearable",
                badgeColor      = MaterialTheme.colorScheme.tertiaryContainer,
                badgeTextColor  = MaterialTheme.colorScheme.onTertiaryContainer
            )

            StorageItemCard(
                icon      = Icons.Default.Storage,
                iconTint  = dbColor,
                iconBg    = dbColor.copy(alpha = 0.15f),
                title     = "App Database",
                subtitle  = "${formatSize(s.databaseBytes)} · Playlists & metadata",
                badge     = null,
                badgeColor      = Color.Transparent,
                badgeTextColor  = Color.Transparent
            )

            StorageItemCard(
                icon      = Icons.Default.Download,
                iconTint  = dlColor,
                iconBg    = dlColor.copy(alpha = 0.15f),
                title     = "Downloaded Videos",
                subtitle  = "${formatSize(s.downloadedFilesBytes)} · Video files stored on device",
                badge     = null,
                badgeColor      = Color.Transparent,
                badgeTextColor  = Color.Transparent
            )

            // ── Actions section ──────────────────────────────────────────────
            Text(
                text       = "Actions",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier.padding(top = 6.dp, start = 4.dp)
            )

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier          = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment  = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.error,
                                modifier           = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = "Clear Thumbnail Cache",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text     = "Frees ${formatSize(s.thumbnailBytes)} · Thumbnails regenerate as you browse",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick  = { showClearDlg = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = s.thumbnailBytes > 0L && !isClearing,
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor   = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onError
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isClearing) "Clearing…" else "Clear Cache")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Clear confirmation dialog ─────────────────────────────────────────────
    if (showClearDlg) {
        AlertDialog(
            onDismissRequest = { showClearDlg = false },
            icon = {
                Icon(
                    imageVector        = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Clear Thumbnail Cache?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text  = "This will delete ${formatSize(stats?.thumbnailBytes ?: 0L)} of cached thumbnail images. " +
                            "They will be automatically re-generated as you browse your library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDlg = false
                        isClearing   = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ThumbnailDiskCache.clear(context)
                                ThumbnailCache.clear()
                            }
                            stats      = calculateStats(context)
                            isClearing = false
                            snackbar.showSnackbar("Thumbnail cache cleared")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDlg = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Private composable helpers ────────────────────────────────────────────────

@Composable
private fun StorageLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StorageItemCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    badgeTextColor: Color
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = subtitle,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text       = badge,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = badgeTextColor
                    )
                }
            }
        }
    }
}
