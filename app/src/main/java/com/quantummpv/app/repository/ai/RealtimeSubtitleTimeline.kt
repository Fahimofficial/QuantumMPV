/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository.ai

import java.util.Locale

internal data class RealtimeSubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val sourceText: String,
  val displayText: String = sourceText,
)

internal class RealtimeSubtitleTimeline {
  private val cues = mutableListOf<RealtimeSubtitleCue>()

  fun absoluteCandidates(
    chunkStartMs: Long,
    chunkDurationMs: Long,
    segments: List<SpeechSegment>,
  ): List<RealtimeSubtitleCue> =
    segments.mapNotNull { segment ->
      val sourceText = segment.text.trim()
      if (sourceText.isEmpty()) return@mapNotNull null

      val relativeStart = segment.startMs.coerceIn(0L, chunkDurationMs)
      val relativeEnd = segment.endMs.coerceIn(relativeStart, chunkDurationMs)
      if (relativeEnd <= relativeStart) return@mapNotNull null

      RealtimeSubtitleCue(
        startMs = chunkStartMs + relativeStart,
        endMs = chunkStartMs + relativeEnd,
        sourceText = sourceText,
      )
    }

  fun merge(incoming: List<RealtimeSubtitleCue>): List<RealtimeSubtitleCue> {
    incoming.forEach { candidate ->
      val duplicateIndex =
        cues.indexOfFirst { existing ->
          overlaps(existing, candidate) && isEquivalentText(existing.sourceText, candidate.sourceText)
        }
      if (duplicateIndex < 0) {
        cues += candidate
      } else {
        val existing = cues[duplicateIndex]
        val preferred = if (candidate.sourceText.length > existing.sourceText.length) candidate else existing
        cues[duplicateIndex] =
          preferred.copy(
            startMs = minOf(existing.startMs, candidate.startMs),
            endMs = maxOf(existing.endMs, candidate.endMs),
          )
      }
    }
    cues.sortBy(RealtimeSubtitleCue::startMs)
    return cues.toList()
  }

  private fun overlaps(
    first: RealtimeSubtitleCue,
    second: RealtimeSubtitleCue,
  ): Boolean = minOf(first.endMs, second.endMs) > maxOf(first.startMs, second.startMs)

  private fun isEquivalentText(
    first: String,
    second: String,
  ): Boolean {
    val normalizedFirst = normalize(first)
    val normalizedSecond = normalize(second)
    if (normalizedFirst == normalizedSecond) return true
    if (normalizedFirst.isEmpty() || normalizedSecond.isEmpty()) return false

    val shorter = if (normalizedFirst.length <= normalizedSecond.length) normalizedFirst else normalizedSecond
    val longer = if (normalizedFirst.length > normalizedSecond.length) normalizedFirst else normalizedSecond
    return shorter.length >= longer.length * 3 / 4 && longer.contains(shorter)
  }

  private fun normalize(text: String): String =
    text
      .lowercase(Locale.ROOT)
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .trim()
      .replace(Regex("\\s+"), " ")
}

internal fun List<RealtimeSubtitleCue>.toSrt(): String =
  mapIndexed { index, cue ->
    "${index + 1}\n${formatSrtTime(cue.startMs)} --> ${formatSrtTime(cue.endMs)}\n${cue.displayText.trim()}"
  }.joinToString("\n\n")

private fun formatSrtTime(milliseconds: Long): String {
  val hours = milliseconds / 3_600_000
  val minutes = (milliseconds % 3_600_000) / 60_000
  val seconds = (milliseconds % 60_000) / 1000
  val millis = milliseconds % 1000
  return "%02d:%02d:%02d,%03d".format(Locale.ROOT, hours, minutes, seconds, millis)
}
