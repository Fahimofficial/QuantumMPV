package com.quantummpv.app.domain.playbackengine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Minimal Media3 backend used for formats with a stable Media3 path.
 *
 * This backend is deliberately independent from the existing MPV session. The caller can keep
 * MPV as the default and switch back if Media3 preparation or playback fails.
 */
class Media3PlaybackEngine(context: Context) : PlaybackEngine {
  private val player = ExoPlayer.Builder(context.applicationContext).build()
  private var attachedView: PlayerView? = null

  override val kind: PlaybackEngineKind = PlaybackEngineKind.MEDIA3

  override fun attach(view: PlayerView) {
    attachedView?.player = null
    attachedView = view
    view.player = player
  }

  override fun prepare(request: PlaybackEngineRequest) {
    val metadata = MediaMetadata.Builder()
      .setTitle(request.uri.lastPathSegment)
      .build()
    val builder = MediaItem.Builder()
      .setUri(request.uri)
      .setMediaMetadata(metadata)
    request.mimeType?.takeIf(String::isNotBlank)?.let(builder::setMimeType)
    player.setMediaItem(builder.build())
    player.seekTo(request.startPositionMs.coerceAtLeast(0L))
    player.playWhenReady = request.playWhenReady
    player.prepare()
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

  override fun release() {
    attachedView?.player = null
    attachedView = null
    player.release()
  }

  companion object {
    fun mimeTypeForExtension(extension: String?): String? = when (extension?.lowercase()) {
      "mp4", "m4v" -> MimeTypes.VIDEO_MP4
      "webm" -> MimeTypes.VIDEO_WEBM
      else -> null
    }
  }
}
