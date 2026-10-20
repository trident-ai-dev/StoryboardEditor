package com.storyboard.editor.domain.model

import java.util.UUID

/** A still-image project. Its duration stays in sync with the current clip list. */
data class TimelineProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val videoClips: List<VideoClip> = emptyList(),
    val audioTrack: AudioTrack? = null,
    val transitions: List<ClipTransition> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Duration of the rendered image sequence, including each clip's playback speed. */
    val durationMs: Long
        get() = videoClips.sumOf { it.effectiveDurationMs.coerceAtLeast(0L) }
}

data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val uri: android.net.Uri,
    val fileName: String,
    val durationMs: Long,
    val startOffsetMs: Long = 0L,
    val volume: Float = 1f
)

data class ClipTransition(
    val type: TransitionType = TransitionType.FADE,
    val durationMs: Long = 500L
)

enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    CROSSFADE("Crossfade"),
    ZOOM("Zoom"),
    SLIDE("Slide"),
    DISSOLVE("Dissolve")
}

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val bitRate: Int = 20_000_000
)

enum class ExportResolution(val width: Int, val height: Int, val label: String) {
    RES_720P(1280, 720, "720p"),
    RES_1080P(1920, 1080, "1080p"),
    RES_1440P(2560, 1440, "1440p")
}

enum class FrameRate(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}
