package app.gyrolet.mpvrx.domain.playbackengine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Playback backend contract for incremental engine integration. */
interface PlaybackEngine {
  val kind: PlaybackEngineKind

  fun attach(playerView: PlayerView)
  fun prepare(request: PlaybackEngineRequest, autoPlay: Boolean = false)
  fun seekTo(positionMs: Long)
  fun release()
}

enum class PlaybackEngineKind {
  MPV,
  MEDIA3,
}

data class PlaybackEngineRequest(
  val uri: String,
  val mimeType: String? = null,
  val preferredEngine: PlaybackEngineKind = PlaybackEngineKind.MPV,
)

/** Conservative routing: Media3 is opt-in and MPV remains the fallback. */
object PlaybackEngineRouter {
  fun route(request: PlaybackEngineRequest): PlaybackEngineKind =
    if (request.preferredEngine == PlaybackEngineKind.MEDIA3 && isMedia3Supported(request)) {
      PlaybackEngineKind.MEDIA3
    } else {
      PlaybackEngineKind.MPV
    }

  fun isMedia3Supported(request: PlaybackEngineRequest): Boolean {
    val uri = request.uri.trim()
    val scheme = uri.substringBefore(":", missingDelimiterValue = "").lowercase()
    if (scheme !in setOf("content", "file", "http", "https")) return false
    if (request.mimeType?.startsWith("video/", ignoreCase = true) == true) return true

    val path = uri.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")
  }
}

object PlaybackEngineFactory {
  fun create(context: Context, kind: PlaybackEngineKind): PlaybackEngine? =
    when (kind) {
      PlaybackEngineKind.MPV -> null
      PlaybackEngineKind.MEDIA3 -> Media3PlaybackEngine(context)
    }
}

class Media3PlaybackEngine(context: Context) : PlaybackEngine {
  override val kind = PlaybackEngineKind.MEDIA3

  private val player = ExoPlayer.Builder(context.applicationContext).build()

  override fun attach(playerView: PlayerView) {
    playerView.player = player
  }

  override fun prepare(request: PlaybackEngineRequest, autoPlay: Boolean) {
    val mediaItem = MediaItem.Builder()
      .setUri(request.uri)
      .apply { request.mimeType?.let { setMimeType(it) } }
      .build()
    player.setMediaItem(mediaItem)
    player.playWhenReady = autoPlay
    player.prepare()
  }

  override fun seekTo(positionMs: Long) {
    player.seekTo(positionMs.coerceAtLeast(0L))
  }

  override fun release() {
    player.release()
  }
}

/** Alias for UI code that wants an explicit Media3 view type. */
typealias Media3PlayerView = PlayerView

/** AndroidX Media3 is intentionally not connected to PlayerActivity yet. */
const val MEDIA3_INTEGRATION_STATUS = "foundation-only"
