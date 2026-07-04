package com.storyboard.editor.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyboard.editor.data.repository.ProjectRepository
import com.storyboard.editor.domain.model.ClipPosition
import com.storyboard.editor.domain.model.ClipTransition
import com.storyboard.editor.domain.model.TimelineProject
import com.storyboard.editor.domain.model.TransitionType
import com.storyboard.editor.domain.model.VideoClip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val project: TimelineProject = TimelineProject(),
    val selectedClipId: String? = null,
    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val timelineZoom: Float = 1f,
    val showInspector: Boolean = false,
    val showTransitionPicker: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<TimelineProject>()
    private val redoStack = ArrayDeque<TimelineProject>()

    init {
        loadProject()
    }

    private fun snapshot() {
        val current = repository.getCurrentProject()
        undoStack.add(current.copy())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = repository.getCurrentProject()
        redoStack.add(current.copy())
        val previous = undoStack.removeLast()
        repository.updateProject(previous)
        loadProject()
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = repository.getCurrentProject()
        undoStack.add(current.copy())
        val next = redoStack.removeLast()
        repository.updateProject(next)
        loadProject()
        updateUndoRedoState()
    }

    fun loadProject() {
        val project = repository.getCurrentProject()
        _uiState.value = _uiState.value.copy(project = project)
    }

    fun selectClip(clipId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedClipId = clipId,
            showInspector = clipId != null
        )
    }

    fun updateClipDuration(clipId: String, newDurationMs: Long) {
        snapshot()
        val clips = _uiState.value.project.videoClips.map { clip ->
            if (clip.id == clipId) clip.copy(durationMs = newDurationMs.coerceAtLeast(500))
            else clip
        }
        repository.updateClips(clips)
        loadProject()
    }

    fun updateClipSpeed(clipId: String, speed: Float) {
        snapshot()
        val clips = _uiState.value.project.videoClips.map { clip ->
            if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.25f, 4f))
            else clip
        }
        repository.updateClips(clips)
        loadProject()
    }

    fun updateClipPosition(clipId: String, position: ClipPosition) {
        snapshot()
        val clips = _uiState.value.project.videoClips.map { clip ->
            if (clip.id == clipId) clip.copy(position = position)
            else clip
        }
        repository.updateClips(clips)
        loadProject()
    }

    fun updateClipTransition(clipId: String, transition: ClipTransition) {
        snapshot()
        val clips = _uiState.value.project.videoClips.map { clip ->
            if (clip.id == clipId) clip.copy(transition = transition)
            else clip
        }
        repository.updateClips(clips)
        loadProject()
    }

    fun setTransitionType(transitionType: TransitionType) {
        _uiState.value = _uiState.value.copy(showTransitionPicker = false)
        val selectedId = _uiState.value.selectedClipId ?: return
        val currentTransition = _uiState.value.project.videoClips
            .find { it.id == selectedId }?.transition
        updateClipTransition(
            selectedId,
            ClipTransition(type = transitionType, durationMs = currentTransition?.durationMs ?: 500L)
        )
    }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        snapshot()
        repository.reorderClips(fromIndex, toIndex)
        loadProject()
    }

    fun removeClip(clipId: String) {
        snapshot()
        repository.removeClip(clipId)
        if (_uiState.value.selectedClipId == clipId) {
            _uiState.value = _uiState.value.copy(selectedClipId = null)
        }
        loadProject()
    }

    fun splitClip(clipId: String, splitTimeMs: Long) {
        snapshot()
        repository.splitClip(clipId, splitTimeMs)
        loadProject()
    }

    fun duplicateClip(clipId: String) {
        snapshot()
        repository.duplicateClip(clipId)
        loadProject()
    }

    fun setPlaying(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    fun updateCurrentTime(timeMs: Long) {
        _uiState.value = _uiState.value.copy(currentTimeMs = timeMs)
    }

    fun updateZoom(zoom: Float) {
        _uiState.value = _uiState.value.copy(timelineZoom = zoom.coerceIn(0.5f, 5f))
    }

    fun toggleInspector() {
        _uiState.value = _uiState.value.copy(showInspector = !_uiState.value.showInspector)
    }

    fun toggleTransitionPicker() {
        _uiState.value = _uiState.value.copy(showTransitionPicker = !_uiState.value.showTransitionPicker)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}