import re

with open("app/src/main/java/com/quantummpv/app/utils/media/MediaSearchEngine.kt", "r") as f:
    content = f.read()

# Replace MediaSearchEngine entirely
new_content = """/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.utils.media

import android.net.Uri
import com.quantummpv.app.database.dao.MediaIndexDao
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.utils.FormatUtils
import com.quantummpv.app.utils.storage.FileTypeUtils
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MediaSearchEngine : KoinComponent {
  private val mediaIndexDao: MediaIndexDao by inject()

  /**
   * Global search using SQLite FTS. Replaces the legacy memory-intensive fuzzy matcher.
   */
  suspend fun search(
    query: String,
    limit: Int = 50,
  ): List<Any> {
    if (query.isBlank()) return emptyList()
    val ftsQuery = "*$query*"
    val results = mediaIndexDao.searchMedia(ftsQuery).first()
    return results.take(limit).map { entity ->
      Video(
        id = entity.uri.hashCode().toLong(),
        title = entity.displayName.substringBeforeLast("."),
        displayName = entity.displayName,
        path = entity.path,
        uri = Uri.parse(entity.uri),
        duration = entity.durationMs ?: 0L,
        durationFormatted = FormatUtils.formatDuration(entity.durationMs ?: 0L),
        size = entity.size,
        sizeFormatted = FormatUtils.formatFileSize(entity.size),
        dateModified = entity.lastModified,
        dateAdded = entity.lastModified,
        mimeType = FileTypeUtils.getMimeTypeFromExtension(entity.extension),
        bucketId = entity.parentFolder,
        bucketDisplayName = entity.parentFolder.substringAfterLast('/'),
        width = 0,
        height = 0,
        fps = 0f,
        resolution = "",
        isAudio = entity.mediaType == 1
      )
    }
  }

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
"""
with open("app/src/main/java/com/quantummpv/app/utils/media/MediaSearchEngine.kt", "w") as f:
    f.write(new_content)
