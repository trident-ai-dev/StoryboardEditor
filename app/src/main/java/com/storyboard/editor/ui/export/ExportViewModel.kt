package com.storyboard.editor.ui.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
    val isSavingToGallery: Boolean = false,
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
        if (state.project.videoClips.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Add at least one image clip before exporting.")
            return
        }

        val outputDir = File(context.cacheDir, "exports")
        if ((!outputDir.isDirectory && !outputDir.mkdirs()) || !outputDir.canWrite()) {
            _uiState.value = state.copy(errorMessage = "Cannot create a writable export directory.")
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDir, "storyboard_$timestamp.mp4")
        val projectWithSettings = state.project.copy(
            exportSettings = state.project.exportSettings.copy(
                resolution = state.selectedResolution,
                frameRate = state.selectedFrameRate
            )
        )

        _uiState.value = state.copy(
            gallerySaved = false,
            isSavingToGallery = false,
            errorMessage = null,
            renderProgress = RenderProgress(
                state = RenderState.PREPARING,
                totalClips = projectWithSettings.videoClips.size,
                message = "Preparing render..."
            )
        )
        viewModelScope.launch {
            // Reflect live progress in the screen instead of waiting for render() to return.
            val progressJob = launch {
                videoRenderer.progress.collect { progress ->
                    _uiState.value = _uiState.value.copy(renderProgress = progress)
                }
            }
            val result = try {
                videoRenderer.render(project = projectWithSettings, outputFile = outputFile)
            } finally {
                progressJob.cancelAndJoin()
            }
            _uiState.value = _uiState.value.copy(
                renderProgress = result,
                gallerySaved = false,
                isSavingToGallery = result.state == RenderState.COMPLETED
            )
            if (result.state == RenderState.COMPLETED) saveToGallery(outputFile)
        }
    }

    private suspend fun saveToGallery(file: File) {
        // Large videos can be hundreds of megabytes; don't copy them on the UI thread.
        val saveError = withContext(Dispatchers.IO) {
            var insertedUri: Uri? = null
            try {
                if (!file.isFile || file.length() <= 0L) {
                    throw IOException("The export file is missing or empty.")
                }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Storyboard")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Android did not create a Gallery destination.")
                insertedUri = destination
                val outputStream = resolver.openOutputStream(destination, "w")
                    ?: throw IOException("Could not open the Gallery destination for writing.")
                val copied = outputStream.use { output ->
                    file.inputStream().use { source -> source.copyTo(output) }
                }
                if (copied != file.length()) {
                    throw IOException("The saved video size does not match the export size.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    if (resolver.update(destination, publishValues, null, null) <= 0) {
                        throw IOException("Could not publish the saved video to the Gallery.")
                    }
                }
                null
            } catch (e: Exception) {
                insertedUri?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                "Failed to save export to Gallery: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        _uiState.value = _uiState.value.copy(
            gallerySaved = saveError == null,
            isSavingToGallery = false,
            errorMessage = saveError
        )
    }

    fun retrySaveToGallery() {
        val outputFile = _uiState.value.renderProgress.outputFile ?: return
        if (_uiState.value.isSavingToGallery) return
        _uiState.value = _uiState.value.copy(isSavingToGallery = true, errorMessage = null)
        viewModelScope.launch { saveToGallery(outputFile) }
    }

    fun cancelExport() {
        videoRenderer.cancel()
    }

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}