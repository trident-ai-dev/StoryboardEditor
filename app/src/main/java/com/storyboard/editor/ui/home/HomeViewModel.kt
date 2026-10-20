package com.storyboard.editor.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyboard.editor.data.converter.TimestampParser
import com.storyboard.editor.data.model.TimestampParseResult
import com.storyboard.editor.data.repository.ProjectRepository
import com.storyboard.editor.domain.model.AudioTrack
import com.storyboard.editor.domain.model.TimelineProject
import com.storyboard.editor.domain.model.VideoClip
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val projectName: String = "Untitled Project",
    val importedImages: List<ImportedImage> = emptyList(),
    val voiceover: AudioTrack? = null,
    val timestampInput: String = "",
    val parseResult: TimestampParseResult? = null,
    val autoBuilt: Boolean = false,
    val mismatchMessage: String? = null,
    val errorMessage: String? = null
)

data class ImportedImage(
    val uri: Uri,
    val fileName: String,
    val index: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val timestampParser: TimestampParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val resolvedImages = withContext(Dispatchers.IO) {
                    uris.map { uri -> ImportedImage(uri = uri, fileName = getFileName(uri), index = 0) }
                }
                val current = _uiState.value
                val newImages = resolvedImages.mapIndexed { index, image ->
                    image.copy(index = current.importedImages.size + index)
                }
                _uiState.value = current.copy(
                    importedImages = current.importedImages + newImages,
                    autoBuilt = false,
                    mismatchMessage = null,
                    errorMessage = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not read the selected images: ${error.message ?: "unknown error"}"
                )
            }
        }
    }

    fun removeImage(index: Int) {
        val images = _uiState.value.importedImages.toMutableList()
        if (index in images.indices) {
            images.removeAt(index)
            _uiState.value = _uiState.value.copy(
                importedImages = images.reIndex(),
                autoBuilt = false
            )
        }
    }

    fun reorderImages(fromIndex: Int, toIndex: Int) {
        val images = _uiState.value.importedImages.toMutableList()
        if (fromIndex in images.indices && toIndex in images.indices) {
            val item = images.removeAt(fromIndex)
            images.add(toIndex, item)
            _uiState.value = _uiState.value.copy(
                importedImages = images.reIndex(),
                autoBuilt = false
            )
        }
    }

    fun replaceImage(index: Int, newUri: Uri) {
        viewModelScope.launch {
            val fileName = withContext(Dispatchers.IO) { getFileName(newUri) }
            val images = _uiState.value.importedImages.toMutableList()
            if (index in images.indices) {
                images[index] = images[index].copy(uri = newUri, fileName = fileName)
                _uiState.value = _uiState.value.copy(importedImages = images, autoBuilt = false)
            }
        }
    }

    fun importVoiceover(uri: Uri) {
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) {
                    val fileName = getFileName(uri)
                    val metadataRetriever = android.media.MediaMetadataRetriever()
                    try {
                        metadataRetriever.setDataSource(context, uri)
                        val durationStr = metadataRetriever.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                        )
                        val durationMs = durationStr?.toLongOrNull() ?: 0L
                        AudioTrack(uri = uri, fileName = fileName, durationMs = durationMs)
                    } finally {
                        metadataRetriever.release()
                    }
                }
                _uiState.value = _uiState.value.copy(voiceover = track, autoBuilt = false, errorMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to read audio file: ${error.message ?: "unknown error"}"
                )
            }
        }
    }

    fun removeVoiceover() {
        _uiState.value = _uiState.value.copy(voiceover = null)
    }

    fun updateTimestampInput(input: String) {
        _uiState.value = _uiState.value.copy(
            timestampInput = input,
            parseResult = null,
            autoBuilt = false,
            mismatchMessage = null,
            errorMessage = null
        )
    }

    fun parseTimestamps() {
        val result = timestampParser.parse(_uiState.value.timestampInput)
        _uiState.value = _uiState.value.copy(parseResult = result)
    }

    fun autoBuildTimeline(): TimelineProject? {
        val state = _uiState.value
        val timestamps = timestampParser.parse(state.timestampInput)
        val images = state.importedImages
        val voiceover = state.voiceover

        if (timestamps.errors.isNotEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Fix timestamp errors before building timeline"
            )
            return null
        }

        if (images.isEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Import at least one image first"
            )
            return null
        }

        if (timestamps.timestamps.isEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Enter at least one timestamp"
            )
            return null
        }

        if (images.size > timestamps.timestamps.size) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                mismatchMessage = "More images (${images.size}) than timestamps (${timestamps.timestamps.size}). " +
                        "Extra images will be ignored. Add more timestamps or remove images.",
                autoBuilt = false
            )
            return null
        }

        if (timestamps.timestamps.size > images.size) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                mismatchMessage = "More timestamps (${timestamps.timestamps.size}) than images (${images.size}). " +
                        "Extra timestamps will be ignored. Add more images or remove timestamps.",
                autoBuilt = false
            )
            return null
        }

        val clips = images.zip(timestamps.timestamps) { image, timestamp ->
            VideoClip(
                imageUri = image.uri,
                imageFileName = image.fileName,
                startTimeMs = timestamp.startMs,
                durationMs = timestamp.durationMs
            )
        }

        val project = TimelineProject(
            name = state.projectName,
            videoClips = clips,
            audioTrack = voiceover
        )

        repository.updateProject(project)

        _uiState.value = state.copy(
            parseResult = timestamps,
            autoBuilt = true,
            mismatchMessage = null,
            errorMessage = null
        )

        return project
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearMismatch() {
        _uiState.value = _uiState.value.copy(mismatchMessage = null)
    }

    fun forceBuildTimeline(): TimelineProject? {
        val state = _uiState.value
        val timestamps = timestampParser.parse(state.timestampInput)
        val images = state.importedImages

        if (timestamps.errors.isNotEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Fix timestamp errors before building timeline"
            )
            return null
        }
        if (images.isEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Import at least one image first"
            )
            return null
        }
        if (timestamps.timestamps.isEmpty()) {
            _uiState.value = state.copy(
                parseResult = timestamps,
                errorMessage = "Enter at least one valid timestamp"
            )
            return null
        }

        val pairedCount = minOf(images.size, timestamps.timestamps.size)
        if (pairedCount == 0) return null
        val clips = images.take(pairedCount).zip(timestamps.timestamps.take(pairedCount)) { image, timestamp ->
            VideoClip(
                imageUri = image.uri,
                imageFileName = image.fileName,
                startTimeMs = timestamp.startMs,
                durationMs = timestamp.durationMs
            )
        }
        val project = TimelineProject(
            name = state.projectName,
            videoClips = clips,
            audioTrack = state.voiceover
        )
        repository.updateProject(project)
        _uiState.value = state.copy(
            parseResult = timestamps,
            autoBuilt = true,
            mismatchMessage = null,
            errorMessage = null
        )
        return project
    }

    private fun getFileName(uri: Uri): String {
        return try {
            var name = "unknown"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: "unknown"
                }
            }
            name.ifBlank { "unknown" }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun List<ImportedImage>.reIndex(): List<ImportedImage> {
        return mapIndexed { index, image -> image.copy(index = index) }
    }
}