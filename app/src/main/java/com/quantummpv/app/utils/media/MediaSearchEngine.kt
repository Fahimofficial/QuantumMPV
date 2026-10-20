/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.utils.media

import com.quantummpv.app.domain.media.model.Video

/** In-memory text search over an already-loaded list of videos. */
object MediaSearchEngine {
  /** Searches a supplied local collection using simple text matching. */
  fun searchVideos(
    query: String,
    videos: List<Video>,
    limit: Int = 50,
  ): List<Video> {
    if (query.isBlank()) return emptyList()
    val q = query.trim().lowercase()
    return videos
      .filter { it.displayName.lowercase().contains(q) }
      .take(limit)
  }
}
