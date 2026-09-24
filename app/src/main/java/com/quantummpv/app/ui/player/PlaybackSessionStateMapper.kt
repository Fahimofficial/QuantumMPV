package com.quantummpv.app.ui.player

import com.quantummpv.app.utils.UrlSanitizer
import `is`.xyz.mpv.MPVNode

/** Pure state mapping logic extracted from PlaybackSession for testability. */
internal object PlaybackSessionStateMapper {

  /**
   * Resolves the next playback phase and error message upon receiving an END_FILE event.
   */
  fun resolveEndFileState(
    reason: PlaybackSession.EndFileReason,
    loadedGeneration: Long,
    currentGeneration: Long,
    activeGeneration: Long,
    parsedError: String?,
  ): Pair<PlaybackPhase, String?> {
    if (activeGeneration != currentGeneration) {
      // The session has already moved on; ignore the event.
      return Pair(PlaybackPhase.LOADING, null)
    }

    if (reason == PlaybackSession.EndFileReason.REDIRECT && loadedGeneration != currentGeneration) {
      // Redirects emit END_FILE before mpv starts the resolved target. Preserve LOADING;
      // the following START_FILE belongs to the same app-level generation.
      return Pair(PlaybackPhase.LOADING, null)
    }

    val failedBeforeReady = loadedGeneration != currentGeneration
    val isFailure =
      reason == PlaybackSession.EndFileReason.ERROR ||
        (failedBeforeReady && reason !in setOf(PlaybackSession.EndFileReason.STOP, PlaybackSession.EndFileReason.QUIT))

    val error =
      if (isFailure) {
        UrlSanitizer.sanitizeExceptionMessage(parsedError)
          ?: "Playback ended before the media became ready (${reason.name.lowercase()})"
      } else {
        null
      }

    val phase = if (isFailure) PlaybackPhase.ERROR else PlaybackPhase.IDLE
    return Pair(phase, error)
  }

  fun parseEndFileError(data: MPVNode): String? =
    sequenceOf(data["error"], data["file_error"])
      .mapNotNull { node -> node?.asString() ?: node?.asInt()?.toString() }
      .firstOrNull { value -> value.isNotBlank() && value != "0" }
}
