# ─────────────────────────────────────────────────────────────────────────────
# StreamCache — ProGuard / R8 Rules
# ─────────────────────────────────────────────────────────────────────────────

# Keep source file names + line numbers in crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Moshi ────────────────────────────────────────────────────────────────────
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep class com.squareup.moshi.** { *; }

# ── Retrofit ─────────────────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.*

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# ── Media3 / ExoPlayer ───────────────────────────────────────────────────────
# Media3 ships its own consumer rules, but keep public API surface explicitly
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── App data / model classes ──────────────────────────────────────────────────
# Room entities and DAOs must survive shrinking intact
-keep class com.example.data.** { *; }
-keep class com.example.viewmodel.** { *; }

# ── Kotlin reflection (used by Moshi codegen) ────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Compose / Material ────────────────────────────────────────────────────────
# R8 handles Compose correctly, but suppress known harmless warnings
-dontwarn androidx.compose.**
