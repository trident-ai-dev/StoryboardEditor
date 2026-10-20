package com.storyboard.editor.media.renderer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.storyboard.editor.domain.model.TimelineProject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class VideoRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow(RenderProgress())
    val progress: StateFlow<RenderProgress> = _progress.asStateFlow()

    @Volatile private var currentTransformer: Transformer? = null
    @Volatile private var isCancelled = false

    suspend fun render(project: TimelineProject, outputFile: File): RenderProgress =
        withContext(Dispatchers.Main) {
            isCancelled = false
            _progress.value = RenderProgress(
                state = RenderState.PREPARING,
                totalClips = project.videoClips.size,
                message = "Preparing render..."
            )
            var completedSuccessfully = false

            try {
                if (project.videoClips.isEmpty()) {
                    val failed = RenderProgress(state = RenderState.FAILED, message = "No clips to render")
                    _progress.value = failed
                    return@withContext failed
                }
                if (project.videoClips.any { it.durationMs <= 0L || it.effectiveDurationMs <= 0L }) {
                    val failed = RenderProgress(
                        state = RenderState.FAILED,
                        message = "Every image clip must have a positive duration and playback speed."
                    )
                    _progress.value = failed
                    return@withContext failed
                }

                outputFile.absoluteFile.parentFile?.let { parent ->
                    if (!parent.isDirectory && !parent.mkdirs()) {
                        throw IOException("Could not create the export directory.")
                    }
                }
                if (outputFile.exists() && !outputFile.delete()) {
                    throw IOException("Could not replace the previous export file.")
                }

                val settings = project.exportSettings
                val presentation = Presentation.createForWidthAndHeight(
                    settings.resolution.width,
                    settings.resolution.height,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
                val videoItems = project.videoClips.map { clip ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(clip.imageUri)
                        .setImageDurationMs(clip.effectiveDurationMs)
                        .build()
                    EditedMediaItem.Builder(mediaItem)
                        .setFrameRate(settings.frameRate.fps)
                        .setEffects(Effects(emptyList(), listOf(presentation)))
                        .build()
                }
                val sequences = mutableListOf(
                    EditedMediaItemSequence.Builder(videoItems).build()
                )

                // Mix the imported voiceover over the image sequence and cap it at the video end.
                project.audioTrack?.let { audio ->
                    val videoDurationMs = project.durationMs
                    val audioEndMs = if (audio.durationMs > 0L) {
                        minOf(audio.durationMs, videoDurationMs)
                    } else {
                        videoDurationMs
                    }
                    val audioMediaBuilder = MediaItem.Builder().setUri(audio.uri)
                    if (audioEndMs > 0L) {
                        audioMediaBuilder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setEndPositionMs(audioEndMs)
                                .build()
                        )
                    }
                    val audioItem = EditedMediaItem.Builder(audioMediaBuilder.build())
                        .setRemoveVideo(true)
                        .build()
                    sequences += EditedMediaItemSequence.Builder(audioItem).build()
                }

                val composition = Composition.Builder(sequences).build()
                val latch = CountDownLatch(1)
                var finalError: ExportException? = null
                var finalResult: ExportResult? = null
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(settings.bitRate.coerceAtLeast(1))
                            .build()
                    )
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .build()
                currentTransformer = transformer
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        finalResult = result
                        latch.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        finalError = exception
                        latch.countDown()
                    }
                })

                _progress.value = _progress.value.copy(
                    state = RenderState.RENDERING,
                    message = "Rendering ${project.videoClips.size} image clips..."
                )
                transformer.start(composition, outputFile.absolutePath)

                val progressHolder = ProgressHolder()
                while (latch.count > 0 && !isCancelled) {
                    if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val fraction = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                        _progress.value = _progress.value.copy(
                            progress = fraction,
                            currentClip = (fraction * project.videoClips.size).toInt()
                                .coerceIn(0, project.videoClips.size),
                            message = "Rendering video... ${(fraction * 100).toInt()}%"
                        )
                    }
                    delay(100)
                }

                if (isCancelled) {
                    transformer.cancel()
                    val cancelled = RenderProgress(state = RenderState.CANCELLED, message = "Export cancelled")
                    _progress.value = cancelled
                    return@withContext cancelled
                }
                finalError?.let { error ->
                    val failed = RenderProgress(
                        state = RenderState.FAILED,
                        totalClips = project.videoClips.size,
                        message = "Export failed: ${error.message ?: "unknown Media3 error"}",
                        error = error.message ?: "Unknown Media3 export error"
                    )
                    _progress.value = failed
                    return@withContext failed
                }
                if (finalResult == null) {
                    val failed = RenderProgress(
                        state = RenderState.FAILED,
                        totalClips = project.videoClips.size,
                        message = "Export stopped without a completion result.",
                        error = "Missing Media3 completion result"
                    )
                    _progress.value = failed
                    return@withContext failed
                }

                completedSuccessfully = true
                val completed = RenderProgress(
                    state = RenderState.COMPLETED,
                    totalClips = project.videoClips.size,
                    currentClip = project.videoClips.size,
                    progress = 1f,
                    message = "Export complete!",
                    outputFile = outputFile
                )
                _progress.value = completed
                completed
            } catch (cancelled: CancellationException) {
                currentTransformer?.cancel()
                throw cancelled
            } catch (error: Exception) {
                currentTransformer?.cancel()
                val failed = RenderProgress(
                    state = RenderState.FAILED,
                    totalClips = project.videoClips.size,
                    message = "Export error: ${error.message ?: error.javaClass.simpleName}",
                    error = error.message ?: error.javaClass.simpleName
                )
                _progress.value = failed
                failed
            } finally {
                currentTransformer = null
                if (!completedSuccessfully) outputFile.delete()
            }
        }

    fun cancel() {
        isCancelled = true
        currentTransformer?.cancel()
    }
}
