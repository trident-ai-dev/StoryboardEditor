package com.storyboard.editor.ui.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyboard.editor.data.repository.ProjectRepository
import com.storyboard.editor.domain.model.ExportResolution
import com.storyboard.editor.domain.model.FrameRate
import com.storyboard.editor.domain.model.TimelineProject
import com.storyboard.editor.media.renderer.RenderProgress
import com.storyboard.editor.media.renderer.RenderState
import com.storyboard.editor.media.renderer.VideoRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val project: TimelineProject = TimelineProject(),
    val selectedResolution: ExportResolution = ExportResolution.RES_1080P,
    val selectedFrameRate: FrameRate = FrameRate.FPS_30,
    val renderProgress: RenderProgress = RenderProgress(),
    val gallerySaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val videoRenderer: VideoRenderer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadProject()
    }

    private fun loadProject() {
        val project = repository.getCurrentProject()
        _uiState.value = _uiState.value.copy(project = project)
    }

    fun setResolution(resolution: ExportResolution) {
        _uiState.value = _uiState.value.copy(selectedResolution = resolution)
    }

    fun setFrameRate(frameRate: FrameRate) {
        _uiState.value = _uiState.value.copy(selectedFrameRate = frameRate)
    }

    fun startExport() {
        val state = _uiState.value
        val outputDir = File(context.cacheDir, "exports")
        outputDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDir, "storyboard_$timestamp.mp4")

        val projectWithSettings = state.project.copy(
            exportSettings = state.project.exportSettings.copy(
                resolution = state.selectedResolution,
                frameRate = state.selectedFrameRate
            )
        )

        viewModelScope.launch {
            val result = videoRenderer.render(
                project = projectWithSettings,
                outputFile = outputFile
            )

            _uiState.value = _uiState.value.copy(renderProgress = result)

            if (result.state == RenderState.COMPLETED) {
                saveToGallery(outputFile)
            }
        }
    }

    private fun saveToGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Storyboard")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                _uiState.value = _uiState.value.copy(gallerySaved = true)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to save to gallery: ${e.message}"
            )
        }
    }

    fun cancelExport() {
        videoRenderer.cancel()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}