/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import java.net.URI

/**
 * Decides which playback engine should own a media item without requiring the UI to know
 * engine-specific routing rules.
 */
class PlaybackEngineRouter {
  enum class Preference {
    AUTO,
    MPV,
    MEDIA3,
  }

  fun preferredEngine(
    uri: String,
    mimeType: String? = null,
    preference: Preference = Preference.AUTO,
  ): PlaybackEngineType {
    return when (preference) {
      Preference.MPV -> PlaybackEngineType.MPV
      Preference.MEDIA3 -> PlaybackEngineType.MEDIA3
      Preference.AUTO -> autoRoute(uri, mimeType)
    }
  }

  private fun autoRoute(
    uriString: String,
    mimeType: String?,
  ): PlaybackEngineType {
    val path = runCatching { URI(uriString).path.orEmpty() }.getOrDefault(uriString).lowercase()
    val normalizedMime = mimeType.orEmpty().lowercase()

    // Media3 is the Android-native path for adaptive streaming. MPV remains the default for
    // everything else until a caller explicitly opts into Media3.
    if (
      normalizedMime.contains("application/dash") ||
      normalizedMime.contains("application/x-mpegurl") ||
      normalizedMime.contains("application/vnd.apple.mpegurl") ||
      path.endsWith(".mpd") ||
      path.endsWith(".m3u8")
    ) {
      return PlaybackEngineType.MEDIA3
    }

    return PlaybackEngineType.MPV
  }
}
