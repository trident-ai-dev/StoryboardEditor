# Storyboard Editor

A professional Android timeline video editor that creates videos from images using a real visual timeline editor. Built with Kotlin, Jetpack Compose, OpenGL ES, and MediaCodec.

## Features

- Bulk Timestamp Input - Paste all timestamps at once
- Auto Timeline Building - Maps images to timestamps automatically
- Visual Timeline Editor - Drag, trim, reorder, split, duplicate clips
- Live Preview - Real-time via ExoPlayer
- Audio Track - Voiceover import with waveform
- Clip Inspector - Edit duration, replace images, transitions per clip
- Transitions - Fade, crossfade, zoom, slide, dissolve
- Export - MP4 H.264 at 720p/1080p/1440p, 24/30/60 FPS
- Gallery Save - Auto-saves to Android Gallery via MediaStore

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM with StateFlow + Coroutines
- DI: Hilt
- Rendering: OpenGL ES + MediaCodec
- Preview: Media3 ExoPlayer
- Image Loading: Coil

## Build

```bash
./gradlew assembleDebug
```