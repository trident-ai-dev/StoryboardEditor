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
    /** Effective display duration, protected against corrupt or uninitialized speed values. */
    val effectiveDurationMs: Long
        get() {
            val safeSpeed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
            return (durationMs.toDouble() / safeSpeed).toLong().coerceAtLeast(0L)
        }
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
