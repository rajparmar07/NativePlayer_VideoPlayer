# ─────────────────────────────────────────────────────────────────────────────
# StreamCache — ProGuard / R8 Rules
# ─────────────────────────────────────────────────────────────────────────────

# Enable R8 optimizations
-allowaccessmodification
-repackageclasses 'a'

# Keep source file names + line numbers in crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ── Media3 / ExoPlayer ───────────────────────────────────────────────────────
# Media3 ships its own consumer rules; suppress benign warnings
-dontwarn androidx.media3.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Compose / Material ────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

