package com.storyboard.editor.media.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewManager @Inject constructor(
    private val context: Context
) {
    private var player: ExoPlayer? = null

    fun getPlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
        return player!!
    }

    fun playVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        getPlayer().apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun playWhenReady(playWhenReady: Boolean) {
        player?.playWhenReady = playWhenReady
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun release() {
        player?.release()
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun pause() {
        player?.pause()
    }

    fun stop() {
        player?.apply {
            stop()
            seekTo(0)
        }
    }

    fun stepForward() {
        player?.let {
            val newPos = (it.currentPosition + 500).coerceAtMost(it.duration)
            it.seekTo(newPos)
        }
    }

    fun stepBackward() {
        player?.let {
            val newPos = (it.currentPosition - 500).coerceAtLeast(0)
            it.seekTo(newPos)
        }
    }
}
