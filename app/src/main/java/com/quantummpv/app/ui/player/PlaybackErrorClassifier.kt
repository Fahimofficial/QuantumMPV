/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import java.util.Locale

/** What went wrong, in terms the user can act on. */
enum class PlaybackErrorKind {
  /** The file is gone, unreadable, or permission was revoked. */
  FILE_UNAVAILABLE,

  /** A stream could not be reached or dropped. */
  NETWORK,

  /** Container or codec this build cannot play. */
  UNSUPPORTED_FORMAT,

  /** Decoding or rendering failed; software decoding may help. */
  DECODER,

  /** Opening the media took too long. */
  TIMEOUT,

  UNKNOWN,
}

/** State for the in-player error card. */
data class PlaybackErrorUi(
  val kind: PlaybackErrorKind,
  /** mpv's own wording, shown under "Details". */
  val detail: String?,
  val canRetry: Boolean = true,
  val canTrySoftwareDecoding: Boolean = false,
  val canOpenExternally: Boolean = true,
)

/**
 * Maps mpv's end-file error (error string or `MPV_ERROR_*` code) plus what is known about the
 * source to a [PlaybackErrorKind]. Pure, so it can be unit tested without libmpv.
 */
object PlaybackErrorClassifier {
  private val networkSchemes = setOf("http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtp", "srt", "udp", "tcp", "mms", "mmsh", "mmst", "ftp")

  fun classify(
    error: String?,
    sourceUri: String?,
    localFileMissing: Boolean = false,
    hardwareDecodingActive: Boolean = false,
  ): PlaybackErrorUi {
    val text = error.orEmpty().lowercase(Locale.ROOT)
    val code = error?.trim()?.toIntOrNull()
    val isNetworkSource = sourceUri?.substringBefore(':', "")?.lowercase(Locale.ROOT) in networkSchemes
    val kind =
      when {
        localFileMissing -> PlaybackErrorKind.FILE_UNAVAILABLE
        "timed out" in text || "timeout" in text -> if (isNetworkSource) PlaybackErrorKind.NETWORK else PlaybackErrorKind.TIMEOUT
        code == -17 || "unrecognized file format" in text || "unknown format" in text ||
          code == -18 || "unsupported" in text -> PlaybackErrorKind.UNSUPPORTED_FORMAT
        code == -15 || code == -14 || "video output" in text || "audio output" in text ||
          "decod" in text -> PlaybackErrorKind.DECODER
        "permission" in text || "no such file" in text || "not found" in text || "eacces" in text ->
          if (isNetworkSource) PlaybackErrorKind.NETWORK else PlaybackErrorKind.FILE_UNAVAILABLE
        isNetworkSource && (code == -13 || "loading failed" in text || "network" in text || "i/o" in text ||
          "connection" in text || "http" in text || "tls" in text || "ssl" in text) -> PlaybackErrorKind.NETWORK
        code == -16 || "nothing to play" in text || "no audio or video" in text ->
          if (hardwareDecodingActive) PlaybackErrorKind.DECODER else PlaybackErrorKind.UNSUPPORTED_FORMAT
        code == -13 || "loading failed" in text -> PlaybackErrorKind.UNKNOWN
        else -> PlaybackErrorKind.UNKNOWN
      }
    return PlaybackErrorUi(
      kind = kind,
      detail = error?.takeIf { it.isNotBlank() },
      canRetry = kind != PlaybackErrorKind.UNSUPPORTED_FORMAT || hardwareDecodingActive,
      canTrySoftwareDecoding = hardwareDecodingActive && kind in setOf(PlaybackErrorKind.DECODER, PlaybackErrorKind.UNSUPPORTED_FORMAT, PlaybackErrorKind.UNKNOWN),
      canOpenExternally = kind != PlaybackErrorKind.FILE_UNAVAILABLE,
    )
  }
}
