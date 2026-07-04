package com.storyboard.editor.media.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.*
import com.storyboard.editor.domain.model.TransitionType
import com.storyboard.editor.domain.model.VideoClip
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class ImageSequenceRenderer(private val context: Context) {

    fun render(
        clips: List<VideoClip>,
        audioTrack: com.storyboard.editor.domain.model.AudioTrack?,
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitRate: Int,
        onProgress: (clip: Int, total: Int) -> Unit
    ): Boolean {
        if (clips.isEmpty()) return false

        var mediaCodec: MediaCodec? = null
        var mediaMuxer: MediaMuxer? = null
        var eglDisplay: EGLDisplay? = null
        var eglContext: EGLContext? = null
        var eglSurface: EGLSurface? = null
        var inputSurface: android.view.Surface? = null

        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            mediaMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val videoTrackIndex = mediaMuxer.addTrack(format)
            mediaMuxer.start()

            val totalFrames = clips.sumOf { (it.durationMs * fps / 1000).toInt() }
            var frameIndex = 0
            var clipIndex = 0

            val bufferInfo = MediaCodec.BufferInfo()
            val presentationTimeUsPerFrame = 1_000_000L / fps

            for ((ci, clip) in clips.withIndex()) {
                clipIndex = ci
                val clipFrames = (clip.durationMs * fps / 1000).toInt()

                val bitmap = loadBitmap(clip.imageUri)
                if (bitmap == null) {
                    onProgress(ci, clips.size)
                    frameIndex += clipFrames
                    continue
                }

                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

                for (fi in 0 until clipFrames) {
                    val pts = frameIndex * presentationTimeUsPerFrame
                    val renderResult = renderFrame(resizedBitmap, inputSurface, width, height)
                    if (!renderResult) break
                    frameIndex++
                }

                resizedBitmap.recycle()
                bitmap.recycle()
                onProgress(ci, clips.size)
            }

            if (mediaCodec != null) {
                mediaCodec.signalEndOfInputStream()

                var sawOutputEOS = false
                while (!sawOutputEOS) {
                    val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (frameIndex == totalFrames) break
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                        outputIndex >= 0 -> {
                            val outputBuffer = mediaCodec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                            }
                            mediaCodec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true
                            }
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { mediaCodec?.stop() } catch (_: Exception) {}
            try { mediaCodec?.release() } catch (_: Exception) {}
            try { mediaMuxer?.stop() } catch (_: Exception) {}
            try { mediaMuxer?.release() } catch (_: Exception) {}
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun renderFrame(
        bitmap: Bitmap,
        surface: android.view.Surface?,
        width: Int,
        height: Int
    ): Boolean {
        if (surface == null) return false
        val canvas = surface.lockCanvas(null)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(canvas)
        return true
    }
}
