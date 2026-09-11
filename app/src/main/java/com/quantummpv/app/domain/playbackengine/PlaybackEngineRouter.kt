package com.quantummpv.app.domain.playbackengine

import android.net.Uri
import java.net.URI

/**
 * Chooses a backend without changing the established MPV default.
 *
 * Media3 routing is opt-in until the player UI and persistence adapters are wired to the backend.
 * Unsupported schemes, unknown extensions, playlists, and provider URLs remain on MPV.
 */
object PlaybackEngineRouter {
  fun select(
    uri: Uri,
    mimeType: String? = null,
    media3Enabled: Boolean = false,
  ): PlaybackEngineKind {
    return select(uri.toString(), mimeType, media3Enabled)
  }

  fun select(
    uri: String,
    mimeType: String? = null,
    media3Enabled: Boolean = false,
  ): PlaybackEngineKind {
    if (!media3Enabled || !isSupportedByMedia3(uri, mimeType)) {
      return PlaybackEngineKind.MPV
    }
    return PlaybackEngineKind.MEDIA3
  }

  fun isSupportedByMedia3(uri: Uri, mimeType: String? = null): Boolean {
    return isSupportedByMedia3(uri.toString(), mimeType)
  }

  fun isSupportedByMedia3(uri: String, mimeType: String? = null): Boolean {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase() ?: return false
    if (scheme !in setOf("content", "file", "http", "https")) return false
    if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
    if (mimeType != null) return false
    return parsed.path
      ?.substringAfterLast('.', missingDelimiterValue = "")
      ?.lowercase()
      ?.let { it in SUPPORTED_EXTENSIONS }
      ?: false
  }

  private val SUPPORTED_EXTENSIONS = setOf("mp4", "m4v", "webm")
}
