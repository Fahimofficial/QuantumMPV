package com.quantummpv.app.domain.playbackengine

import android.net.Uri
import androidx.media3.ui.PlayerView

/**
 * Playback backends supported by the player. MPV remains the compatibility fallback.
 */
enum class PlaybackEngineKind {
  MPV,
  MEDIA3,
}

data class PlaybackEngineRequest(
  val uri: Uri,
  val mimeType: String? = null,
  val startPositionMs: Long = 0L,
  val playWhenReady: Boolean = true,
)

interface PlaybackEngine {
  val kind: PlaybackEngineKind

  fun attach(view: PlayerView)

  fun prepare(request: PlaybackEngineRequest)

  fun play()

  fun pause()

  fun seekTo(positionMs: Long)

  fun release()
}
