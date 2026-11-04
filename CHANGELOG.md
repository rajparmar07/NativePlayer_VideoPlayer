# Changelog

All notable changes to **Native Player** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-11-04

### Added
- **Background Playback Toggle**: Added a user setting in Playback Settings to easily configure whether playback continues or pauses when switching apps or pressing Home.
- **Screen Sleep Prevention (Wake Lock)**: Added `android.permission.WAKE_LOCK` support and automated `FLAG_KEEP_SCREEN_ON` management to prevent the display from sleeping while a video is playing.
- **Automated Testing Suite**: Added comprehensive Robolectric unit and lifecycle tests (`WakeLockTest` and `BackgroundPlaybackLifecycleTest`) covering background lifecycle transitions and screen-awake flag management.
- **Enhanced Crash Symbolication**: Configured NDK `debugSymbolLevel = "FULL"` for release builds for better native error diagnostics.

### Fixed
- **Subsequent Seek Freezing Issue**: Resolved a bug where rapid or repeated scrubbing on the timeline caused playback to freeze or stall indefinitely. Implemented seek-settling state tracking, safety timeouts, and seek throttling (100ms–120ms) to prevent `MediaCodec` buffer overloads.
- **Unintended Background Playback**: Fixed an issue where playback unexpectedly continued running in the background when navigating to the Home screen without background playback explicitly enabled.

---

## [1.0.0] - 2026-10-08

### Added
- **Modern Scoped Storage Support**: Full migration to Android Scoped Storage (`READ_MEDIA_VIDEO` and MediaStore consent dialogs), removing `MANAGE_EXTERNAL_STORAGE` for Google Play Store compliance.
- **Live Media Synchronization**: Real-time media library synchronization via `ContentObserver` and `ON_RESUME` lifecycle events.
- **Android 15+ Edge-to-Edge Display**: Seamless edge-to-edge layout compatibility and system bar handling.
- **High-Performance Core Playback**: AndroidX Media3 ExoPlayer integration paired with Jellyfin FFmpeg software decoders for comprehensive container and codec support (MKV, MP4, WebM, DTS, AC3, TrueHD, FLAC, Opus).
- **Material Design 3 & OLED Theme**: Material You dynamic theming and high-contrast OLED black mode.
- **Gesture Controls & Custom Controls**: Intuitive gestures for brightness, volume, seek scrubbing, fast-forward speed boost, and picture-in-picture (PiP).
