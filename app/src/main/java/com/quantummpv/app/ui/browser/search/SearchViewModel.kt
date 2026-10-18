/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.preferences.preference.Preference
import com.quantummpv.app.preferences.preference.PreferenceStore
import com.quantummpv.app.repository.MediaFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Reactive library search.
 *
 * Pipeline: [searchQuery] → trim → debounce (250 ms, skipped for a blank query so clearing the
 * field is instant) → [distinctUntilChanged] → [combine] with the library snapshot → filter on
 * [computeDispatcher] with [mapLatest], so a newer query cancels an in-flight filter.
 *
 * The snapshot is loaded from [folders] with [loadMedia] only while the UI collects [uiState], and
 * reloaded only when the set of folders changes.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
  folders: Flow<List<VideoFolder>>,
  private val loadMedia: suspend (List<VideoFolder>) -> List<Video>,
  private val history: Preference<String>,
  computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  // Folder metadata (counts, sizes) refreshes often while the library scans; files are only reloaded
  // when the set of folders changes, otherwise every refresh would restart the load.
  private val media: Flow<List<Video>> =
    folders
      .distinctUntilChangedBy { list -> list.map { it.bucketId } }
      .mapLatest { list ->
        try {
          loadMedia(list)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load media for search", e)
          emptyList()
        }
      }

  /** Null until the first file load completes. */
  private val catalog: StateFlow<SearchCatalog?> =
    combine<List<VideoFolder>, List<Video>, SearchCatalog?>(folders, media) { folderList, mediaList ->
      SearchCatalog(folders = folderList, media = mediaList)
    }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  private val debouncedQuery: Flow<String> =
    _searchQuery
      .map { it.trim() }
      .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
      .distinctUntilChanged()

  val uiState: StateFlow<SearchUiState> =
    combine(debouncedQuery, catalog) { query, snapshot -> query to snapshot }
      .mapLatest { (query, snapshot) ->
        when {
          query.isEmpty() -> SearchUiState.Initial
          snapshot == null -> SearchUiState.Loading
          else -> {
            val results = snapshot.search(query)
            if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Success(results)
          }
        }
      }.distinctUntilChanged()
      .flowOn(computeDispatcher)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState.Initial)

  val recentSearches: StateFlow<List<String>> =
    history
      .changes()
      .map(::decodeHistory)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), decodeHistory(history.get()))

  fun onQueryChange(query: String) {
    _searchQuery.value = query
  }

  /** Records the current (or given) query as a recent search. */
  fun commitQuery(query: String = _searchQuery.value) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val updated =
      listOf(trimmed) + decodeHistory(history.get()).filterNot { it.equals(trimmed, ignoreCase = true) }
    history.set(encodeHistory(updated.take(MAX_RECENT_SEARCHES)))
  }

  fun removeRecentSearch(query: String) {
    history.set(encodeHistory(decodeHistory(history.get()).filterNot { it == query }))
  }

  fun clearRecentSearches() {
    history.set("")
  }

  /** Clears the query when the search session ends; recent searches are kept. */
  fun reset() {
    _searchQuery.value = ""
  }

  companion object {
    private const val TAG = "SearchViewModel"
    internal const val SEARCH_DEBOUNCE_MS = 250L
    private const val STOP_TIMEOUT_MS = 5_000L
    internal const val MAX_RECENT_SEARCHES = 8
    private const val HISTORY_SEPARATOR = '\n'

    internal fun decodeHistory(raw: String): List<String> =
      raw.split(HISTORY_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    internal fun encodeHistory(entries: List<String>): String = entries.joinToString(HISTORY_SEPARATOR.toString())

    fun historyKey(audioOnly: Boolean): String = if (audioOnly) "music_search_history" else "media_search_history"

    /** Builds a search over [folders]; files come from MediaStore plus the filesystem. */
    fun factory(
      application: Application,
      folders: Flow<List<VideoFolder>>,
      audioOnly: Boolean,
      preferenceStore: PreferenceStore,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SearchViewModel(
          folders = folders,
          loadMedia = { list ->
            MediaFileRepository.getVideosForBuckets(
              application,
              list.mapTo(LinkedHashSet()) { it.bucketId },
              includeAudioOverride = if (audioOnly) true else null,
            )
          },
          history = preferenceStore.getString(historyKey(audioOnly), ""),
        ) as T
    }
  }
}
