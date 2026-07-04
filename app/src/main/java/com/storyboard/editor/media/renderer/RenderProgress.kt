package com.storyboard.editor.media.renderer

import java.io.File

enum class RenderState {
    IDLE,
    PREPARING,
    RENDERING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class RenderProgress(
    val state: RenderState = RenderState.IDLE,
    val totalClips: Int = 0,
    val currentClip: Int = 0,
    val progress: Float = 0f,
    val message: String = "",
    val error: String? = null,
    val outputFile: File? = null
)
