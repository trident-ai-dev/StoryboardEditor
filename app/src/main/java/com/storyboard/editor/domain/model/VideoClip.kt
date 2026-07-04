package com.storyboard.editor.domain.model

import android.net.Uri
import java.util.UUID

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: Uri,
    val imageFileName: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val speed: Float = 1f,
    val position: ClipPosition = ClipPosition(),
    val opacity: Float = 1f,
    val transition: ClipTransition? = null
) {
    val effectiveDurationMs: Long get() = (durationMs / speed).toLong()
}

data class ClipPosition(
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val cropRect: CropRect? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)
