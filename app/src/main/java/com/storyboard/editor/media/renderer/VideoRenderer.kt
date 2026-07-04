package com.storyboard.editor.media.renderer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.storyboard.editor.domain.model.TimelineProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class VideoRenderer @Inject constructor(
    private val context: Context
) {
    private val _progress = MutableStateFlow(RenderProgress())
    val progress: StateFlow<RenderProgress> = _progress.asStateFlow()

    private var currentTransformer: Transformer? = null
    private var isCancelled = false

    suspend fun render(
        project: TimelineProject,
        outputFile: File
    ): RenderProgress = withContext(Dispatchers.Main) {
        isCancelled = false
        _progress.value = RenderProgress(
            state = RenderState.PREPARING,
            totalClips = project.videoClips.size,
            message = "Preparing render..."
        )

        try {
            if (project.videoClips.isEmpty()) {
                val fail = RenderProgress(
                    state = RenderState.FAILED,
                    message = "No clips to render"
                )
                _progress.value = fail
                return@withContext fail
            }

            val sequences = project.videoClips.map { clip ->
                val mediaItem = MediaItem.fromUri(clip.imageUri)
                EditedMediaItem.Builder(mediaItem).build()
            }

            val composition = Composition.Builder(
                EditedMediaItemSequence(sequences)
            ).build()

            val latch = CountDownLatch(1)
            var finalResult: ExportResult? = null
            var finalError: ExportException? = null

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setOutputFile(outputFile.absolutePath)
                .build()

            currentTransformer = transformer

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    finalResult = result
                    latch.countDown()
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    finalError = exception
                    latch.countDown()
                }
            })

            _progress.value = _progress.value.copy(
                state = RenderState.RENDERING,
                message = "Rendering video..."
            )

            transformer.start(composition)

            while (latch.count > 0 && !isCancelled) {
                delay(100)
            }

            if (isCancelled) {
                transformer.cancel()
                val cancelled = RenderProgress(
                    state = RenderState.CANCELLED,
                    message = "Export cancelled"
                )
                _progress.value = cancelled
                return@withContext cancelled
            }

            if (finalError != null) {
                val fail = RenderProgress(
                    state = RenderState.FAILED,
                    message = "Export failed: ${finalError.message}",
                    error = finalError.message
                )
                _progress.value = fail
                return@withContext fail
            }

            val done = RenderProgress(
                state = RenderState.COMPLETED,
                totalClips = project.videoClips.size,
                currentClip = project.videoClips.size,
                progress = 1f,
                message = "Export complete!",
                outputFile = outputFile
            )
            _progress.value = done
            done

        } catch (e: Exception) {
            val fail = RenderProgress(
                state = RenderState.FAILED,
                message = "Export error: ${e.message}",
                error = e.message
            )
            _progress.value = fail
            fail
        }
    }

    fun cancel() {
        isCancelled = true
        currentTransformer?.cancel()
    }
}
