/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import com.quantummpv.app.repository.IntroDbSegment
import java.util.Locale

internal data class ChapterSkipMarker(
  val title: String?,
  val startSeconds: Double,
)

internal data class ProviderSkipMarker(
  val type: String?,
  val startSeconds: Double?,
  val endSeconds: Double?,
)

/** Shared, deterministic skip-marker rules kept independent from mpv and Android lifecycle state. */
internal object SkipMarkerResolver {
  private const val EOF_SEEK_GUARD_SECONDS = 0.25

  fun parseCustomKeywords(value: String): List<String> =
    value
      .split(Regex("""[,;\n\r]+"""))
      .map(String::trim)
      .filter(String::isNotEmpty)

  fun classifyTitle(
    title: String?,
    introKeywords: List<String>,
    outroKeywords: List<String>,
    recapKeywords: List<String>,
    creditsKeywords: List<String>,
    previewKeywords: List<String>,
  ): SkipSegmentType? {
    if (title.isNullOrBlank()) return null
    val lowered = title.lowercase(Locale.ROOT)
    val normalizedLatin = lowered.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    val compactLatin = normalizedLatin.replace(" ", "")
    val compactRaw = lowered.replace(Regex("""[\s\p{Punct}・_]+"""), "")

    fun hasKeyword(keywords: List<String>): Boolean =
      keywords.any { rawKeyword ->
        val keyword = rawKeyword.lowercase(Locale.ROOT).trim()
        when {
          keyword.isEmpty() -> false
          keyword.matches(Regex("""[a-z0-9]+""")) ->
            normalizedLatin.contains(Regex("""(?:^|\s)${Regex.escape(keyword)}(?:\s|$)""")) ||
              (keyword.length >= 4 && compactLatin.contains(keyword))
          else -> compactRaw.contains(keyword.replace(" ", ""))
        }
      }

    val hasIntro = hasKeyword(introKeywords)
    val hasRecap = hasKeyword(recapKeywords)
    val hasCredits = hasKeyword(creditsKeywords)
    val hasPreview = hasKeyword(previewKeywords)
    val hasOutro = hasKeyword(outroKeywords)
    return when {
      hasRecap -> SkipSegmentType.RECAP
      hasCredits -> SkipSegmentType.CREDITS
      hasPreview -> SkipSegmentType.PREVIEW
      hasOutro && !hasIntro -> SkipSegmentType.OUTRO
      hasIntro -> SkipSegmentType.INTRO
      else -> null
    }
  }

  fun resolveChapters(
    chapters: List<ChapterSkipMarker>,
    durationSeconds: Double,
    classify: (String?) -> SkipSegmentType?,
  ): List<SkipSegment> {
    if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return emptyList()

    val orderedChapters =
      chapters
        .filter { marker ->
          marker.startSeconds.isFinite() &&
            marker.startSeconds >= 0.0 &&
            marker.startSeconds < durationSeconds
        }.sortedBy(ChapterSkipMarker::startSeconds)

    return orderedChapters.mapIndexedNotNull { index, marker ->
      val type = classify(marker.title) ?: return@mapIndexedNotNull null
      val nextStart =
        orderedChapters
          .asSequence()
          .drop(index + 1)
          .map(ChapterSkipMarker::startSeconds)
          .firstOrNull { it > marker.startSeconds }
      val endSeconds = (nextStart ?: durationSeconds).coerceAtMost(durationSeconds)
      if (endSeconds <= marker.startSeconds) return@mapIndexedNotNull null

      SkipSegment(
        type = type,
        startSeconds = marker.startSeconds,
        endSeconds = endSeconds,
        source = "chapter",
      )
    }
  }

  fun resolveProviderSegment(
    segmentType: String?,
    startSeconds: Double?,
    endSeconds: Double?,
    durationSeconds: Double,
    source: String,
  ): SkipSegment? {
    val marker =
      IntroDbSegment(segmentType = segmentType, start = startSeconds, end = endSeconds)
        .validatedForDuration(durationSeconds) ?: return null
    val type =
      when (marker.segmentType) {
        "intro" -> SkipSegmentType.INTRO
        "recap" -> SkipSegmentType.RECAP
        "outro" -> SkipSegmentType.OUTRO
        "credits" -> SkipSegmentType.CREDITS
        "preview" -> SkipSegmentType.PREVIEW
        else -> return null
      }
    return SkipSegment(
      type = type,
      startSeconds = marker.normalizedStart,
      endSeconds = marker.normalizedEnd,
      source = source,
    )
  }

  fun resolveProviderSegments(
    markers: List<ProviderSkipMarker>,
    durationSeconds: Double,
    source: String,
  ): List<SkipSegment> =
    markers.mapNotNull { marker ->
      resolveProviderSegment(
        segmentType = marker.type,
        startSeconds = marker.startSeconds,
        endSeconds = marker.endSeconds,
        durationSeconds = durationSeconds,
        source = source,
      )
    }

  fun merge(segments: Iterable<SkipSegment>): List<SkipSegment> {
    val merged = mutableListOf<SkipSegment>()
    segments
      .filter { segment ->
        segment.startSeconds.isFinite() &&
          segment.endSeconds.isFinite() &&
          segment.startSeconds >= 0.0 &&
          segment.isValid
      }.sortedWith(
        compareBy<SkipSegment> { it.endSeconds - it.startSeconds }
          .thenBy(SkipSegment::startSeconds)
          .thenBy { it.type.ordinal },
      ).forEach { candidate ->
        val overlaps =
          merged.any { existing ->
            existing.startSeconds < candidate.endSeconds && candidate.startSeconds < existing.endSeconds
          }
        if (!overlaps) merged += candidate
      }
    return merged.sortedBy(SkipSegment::startSeconds)
  }

  /** Avoids seeking to mpv's exact EOF while still allowing a final marker to be skipped. */
  fun seekTarget(
    segment: SkipSegment,
    durationSeconds: Double,
  ): Double {
    if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return segment.endSeconds
    val endSeconds = segment.endSeconds.coerceAtMost(durationSeconds)
    if (endSeconds < durationSeconds) return endSeconds

    val guardedTarget = durationSeconds - EOF_SEEK_GUARD_SECONDS
    return if (guardedTarget > segment.startSeconds) {
      guardedTarget
    } else {
      segment.startSeconds + ((durationSeconds - segment.startSeconds) / 2.0)
    }.coerceIn(segment.startSeconds, endSeconds)
  }
}
