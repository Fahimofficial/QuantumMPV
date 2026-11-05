/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.utils.media

import androidx.compose.runtime.Immutable

/** A display title plus the downloader id that was stripped from it (shown as muted secondary text). */
@Immutable
data class CleanTitle(
  val title: String,
  val id: String?,
)

// One trailing "[id]" as written by yt-dlp style downloaders: no spaces, so "[Official Video]" stays.
private val TRAILING_ID = Regex("""\s*\[([A-Za-z0-9_-]{6,24})]\s*$""")
private val PINTEREST_NUMBER = Regex("""(Pinterest video)\s*#\d+""", RegexOption.IGNORE_CASE)
private const val PINTEREST_FALLBACK = "Pinterest video"

/**
 * Display-only cleanup of a file name stem (no extension): drops one trailing "[id]" and the
 * "#digits" after "Pinterest video", keeping emoji and everything else. Never used to rename files.
 * An empty result falls back to "Pinterest video" for Pinterest names and to the raw name otherwise.
 */
fun cleanTitle(raw: String): CleanTitle {
  val match = TRAILING_ID.find(raw)
  val id = match?.groupValues?.get(1)
  val withoutId = if (match != null) raw.substring(0, match.range.first) else raw
  val title = PINTEREST_NUMBER.replace(withoutId) { it.groupValues[1] }.trim()
  return when {
    title.isNotEmpty() -> CleanTitle(title, id)
    raw.contains("pinterest", ignoreCase = true) -> CleanTitle(PINTEREST_FALLBACK, id)
    else -> CleanTitle(raw.trim(), null)
  }
}
