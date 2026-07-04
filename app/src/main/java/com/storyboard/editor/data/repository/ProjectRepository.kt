package com.storyboard.editor.data.repository

import android.content.Context
import android.net.Uri
import com.storyboard.editor.domain.model.AudioTrack
import com.storyboard.editor.domain.model.ClipTransition
import com.storyboard.editor.domain.model.ExportSettings
import com.storyboard.editor.domain.model.TransitionType
import com.storyboard.editor.domain.model.TimelineProject
import com.storyboard.editor.domain.model.VideoClip
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var currentProject: TimelineProject = TimelineProject()

    fun getCurrentProject(): TimelineProject = currentProject

    fun createNewProject(name: String = "Untitled Project"): TimelineProject {
        currentProject = TimelineProject(name = name)
        return currentProject
    }

    fun updateProject(project: TimelineProject) {
        currentProject = project
    }

    fun addClips(clips: List<VideoClip>) {
        currentProject = currentProject.copy(
            videoClips = currentProject.videoClips + clips
        )
    }

    fun updateClips(clips: List<VideoClip>) {
        currentProject = currentProject.copy(videoClips = clips)
    }

    fun setAudioTrack(audioTrack: AudioTrack?) {
        currentProject = currentProject.copy(audioTrack = audioTrack)
    }

    fun updateExportSettings(settings: ExportSettings) {
        currentProject = currentProject.copy(exportSettings = settings)
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val mutableClips = currentProject.videoClips.toMutableList()
        if (fromIndex in mutableClips.indices && toIndex in mutableClips.indices) {
            val item = mutableClips.removeAt(fromIndex)
            mutableClips.add(toIndex, item)
            currentProject = currentProject.copy(
                videoClips = mutableClips
            )
        }
    }

    fun removeClip(clipId: String) {
        currentProject = currentProject.copy(
            videoClips = currentProject.videoClips.filter { it.id != clipId }
        )
    }

    fun splitClip(clipId: String, splitTimeMs: Long) {
        val clip = currentProject.videoClips.find { it.id == clipId } ?: return
        if (splitTimeMs <= clip.startTimeMs || splitTimeMs >= clip.startTimeMs + clip.durationMs) return

        val firstClip = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            durationMs = splitTimeMs - clip.startTimeMs
        )
        val secondClip = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            startTimeMs = splitTimeMs,
            durationMs = clip.durationMs - (splitTimeMs - clip.startTimeMs)
        )

        val clips = currentProject.videoClips.toMutableList()
        val idx = clips.indexOfFirst { it.id == clipId }
        if (idx >= 0) {
            clips.removeAt(idx)
            clips.add(idx, secondClip)
            clips.add(idx, firstClip)
            currentProject = currentProject.copy(videoClips = clips)
        }
    }

    fun duplicateClip(clipId: String) {
        val clip = currentProject.videoClips.find { it.id == clipId } ?: return
        val duplicate = clip.copy(
            id = java.util.UUID.randomUUID().toString(),
            imageFileName = clip.imageFileName + " (Copy)"
        )
        currentProject = currentProject.copy(
            videoClips = currentProject.videoClips + duplicate
        )
    }
}
