# Storyboard Editor (Android prototype)

An Android prototype for turning timestamped image selections into an MP4 image sequence. It uses Kotlin, Jetpack Compose, AndroidX Media3 Transformer, and H.264 encoding. It is an active prototype, not yet a production-ready full-featured video editor.

## Current scope

- Import images and pair them with timestamp ranges.
- Review and edit a project on a Compose-based timeline.
- Export a timestamped image sequence to MP4 using Media3 Transformer and H.264.
- Select output resolution (720p, 1080p, or 1440p) and frame rate (24, 30, or 60 FPS).
- Include an imported audio track, clipped to the rendered video duration.
- Save a completed export to Android Movies/Storyboard through MediaStore.

## Known limitations

- Project state is held in memory; projects are not yet saved and restored across app restarts. Image/audio metadata lookup and Gallery file copying run off the UI thread.
- Timeline transition, crop/rotation/opacity, audio start offset, and audio volume controls are not all applied during export yet. Do not rely on them for final output.
- CI is configured to compile the app and run parser unit tests. Device-level testing is still needed across Android versions, image providers, audio codecs, and hardware encoders.
- A successful CI build is not a claim that every editing feature is production-ready.

## Build and test

Requires a JDK 17 installation and Android SDK components for compile SDK 35. The official Gradle wrapper JAR is stored as a base64 text asset because the GitHub MCP text-file uploader cannot store binary blobs. The launcher reconstructs it only when missing and verifies its SHA-256 before use, so a separate global Gradle install is not required. Run the launcher through Bash if the checkout does not preserve executable file permissions.

```bash
bash gradlew --no-daemon assembleDebug testDebugUnitTest
```

GitHub Actions runs the same command on pushes and pull requests and uploads the debug APK as a workflow artifact.

## Technical notes

- Kotlin, Jetpack Compose, Material 3, Hilt, coroutines, and AndroidX Media3.
- Minimum Android API: 26; compile/target SDK: 35.
- The current exporter uses Media3 Transformer for the image sequence, output frame rate, output dimensions, requested video bitrate, optional audio sequence, and H.264 output.

## Contributing

Please include a minimal reproduction for reported defects, run the Gradle build and unit tests when you have an Android SDK available, and describe which checks were actually executed. Do not commit API keys, local SDK paths, build outputs, or signing credentials.

## License status

No license has been selected for this repository yet. Do not assume permission to reuse or redistribute the code; contact the maintainer to discuss permission.
