/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Android-native Media3 playback implementation.
 *
 * This engine is intentionally independent of PlayerActivity. It owns an ExoPlayer instance and
 * can be mounted into a supplied container when a caller decides Media3 should handle playback.
 */
@OptIn(UnstableApi::class)
class Media3PlaybackEngine(
  context: Context,
) : PlaybackEngine {
  override val type: PlaybackEngineType = PlaybackEngineType.MEDIA3

  private val player: ExoPlayer =
    ExoPlayer.Builder(context.applicationContext)
      .setMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext))
      .build()

  private var playerView: PlayerView? = null
  private var lastError: PlaybackException? = null
  private var prepared = false

  init {
    player.addListener(
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          lastError = error
        }
      },
    )
  }

  override val isInitialized: Boolean
    get() = prepared && player.playbackState != Player.STATE_IDLE

  override fun attach(container: ViewGroup) {
    val existing = playerView
    if (existing?.parent === container) return

    existing?.let { old ->
      (old.parent as? ViewGroup)?.removeView(old)
    }

    playerView =
      PlayerView(container.context).apply {
        player = this@Media3PlaybackEngine.player
        useController = false
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }

    container.addView(playerView)
  }

  override fun prepare(request: PlaybackRequest) {
    lastError = null
    prepared = false

    val mediaItem =
      MediaItem.Builder()
        .setUri(request.uri)
        .setMediaId(request.uri)
        .setMimeType(request.mimeType)
        .setMediaMetadata(
          MediaMetadata.Builder()
            .setTitle(request.title)
            .build(),
        )
        .build()

    player.setMediaItem(mediaItem)
    player.prepare()
    player.seekTo(request.startPositionMs.coerceAtLeast(0L))
    player.playWhenReady = request.playWhenReady
    prepared = true
  }

  override fun play() {
    player.play()
  }

  override fun pause() {
    player.pause()
  }

  override fun seekTo(positionMs: Long) {
    player.seekTo(positionMs.coerceAtLeast(0L))
  }

  override fun setPlaybackSpeed(speed: Float) {
    player.setPlaybackSpeed(speed.coerceIn(0.1f, 8f))
  }

  override fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

  override fun durationMs(): Long = player.duration.takeIf { it >= 0L } ?: 0L

  override fun release() {
    playerView?.let { view ->
      (view.parent as? ViewGroup)?.removeView(view)
    }
    playerView = null
    prepared = false
    lastError = null
    player.release()
  }

  override fun asView(): View? = playerView

  fun lastError(): PlaybackException? = lastError

  fun player(): ExoPlayer = player
}
