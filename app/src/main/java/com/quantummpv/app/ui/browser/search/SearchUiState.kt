/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.search

import androidx.compose.runtime.Immutable
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder

/** One searchable library entry: a folder or a single video/audio file. */
@Immutable
sealed interface MediaItem {
  val name: String
  val path: String

  @Immutable
  data class Folder(
    val folder: VideoFolder,
  ) : MediaItem {
    override val name: String get() = folder.name
    override val path: String get() = folder.path
  }

  @Immutable
  data class Media(
    val video: Video,
  ) : MediaItem {
    override val name: String get() = video.displayName
    override val path: String get() = video.path
  }
}

/** Search screen state. [Initial] is the only state shown for a blank query. */
@Immutable
sealed interface SearchUiState {
  /** Query is blank: show recent searches, never "No results found". */
  data object Initial : SearchUiState

  /** Query is non-blank but the library snapshot is still loading. */
  data object Loading : SearchUiState

  @Immutable
  data class Success(
    val results: List<MediaItem>,
  ) : SearchUiState

  /** Query is non-blank and nothing matched. */
  data object Empty : SearchUiState
}

/** Folders and media the search runs against, loaded once per folder-list change. */
@Immutable
internal data class SearchCatalog(
  val folders: List<VideoFolder>,
  val media: List<Video>,
)

/**
 * Case-insensitive substring match on name and path. [query] must already be trimmed. Media also
 * match on their title, which for audio is the tag title rather than the file name.
 */
internal fun MediaItem.matches(query: String): Boolean =
  name.contains(query, ignoreCase = true) ||
    path.contains(query, ignoreCase = true) ||
    (this is MediaItem.Media && video.title.contains(query, ignoreCase = true))

/**
 * Folders first, then media. Within each group, names that start with the query rank before names
 * that only contain it, then alphabetically.
 */
internal fun SearchCatalog.search(query: String): List<MediaItem> {
  val q = query.trim()
  if (q.isEmpty()) return emptyList()
  val ranking =
    compareBy<MediaItem>({ !it.name.startsWith(q, ignoreCase = true) }, { it.name.lowercase() })
  val matchingFolders =
    folders.asSequence().map { MediaItem.Folder(it) }.filter { it.matches(q) }.sortedWith(ranking).toList()
  val matchingMedia =
    media.asSequence().map { MediaItem.Media(it) }.filter { it.matches(q) }.sortedWith(ranking).toList()
  return matchingFolders + matchingMedia
}
