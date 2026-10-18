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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.quantummpv.app.R
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.preferences.BrowserPreferences
import com.quantummpv.app.preferences.MediaLayoutMode
import com.quantummpv.app.preferences.preference.PreferenceStore
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.HidesMiniPlayer
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.folderlist.FolderListViewModel
import com.quantummpv.app.ui.browser.states.EmptyState
import com.quantummpv.app.ui.browser.videolist.VideoListScreen
import com.quantummpv.app.ui.components.InlineSearchBar
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.LocalSharedTransitionScope
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.popSafely
import com.quantummpv.app.ui.utils.rememberMorphingCornerShape
import com.quantummpv.app.ui.utils.rememberSearchMorphModifier
import com.quantummpv.app.utils.media.MediaUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Corner radius of the Search bar once the morph from the dock's circular button settles. */
private val SearchBarCornerRadius = 24.dp

/** Half of the 52 dp dock button: the bar starts the morph as that circle. */
private val SearchButtonCornerRadius = 26.dp

/**
 * Full-screen library search reached from the dock's Search button. As its own destination the
 * dock, mini player and quick-play FAB are off screen, so results and keyboard get the full height.
 */
@Serializable
data class MediaSearchScreen(
  val audioOnly: Boolean = false,
) : Screen,
  HidesMiniPlayer {
  @Composable
  override fun Content() {
    MediaSearchContent(
      route = this,
      sharedTransitionScope = LocalSharedTransitionScope.current,
      animatedVisibilityScope = LocalNavAnimatedContentScope.current,
    )
  }
}

/**
 * The [SearchViewModel] for [audioOnly]'s folder list. Both view models are Activity-scoped, so
 * the search reuses the folders the browser already loaded.
 */
@Composable
internal fun rememberMediaSearchViewModel(
  audioOnly: Boolean,
  key: String,
): SearchViewModel {
  val application = LocalContext.current.applicationContext as Application
  val folderListViewModel: FolderListViewModel =
    viewModel(
      key = if (audioOnly) "MusicListViewModel" else "FolderListViewModel",
      factory = FolderListViewModel.factory(application, audioOnly),
    )
  val preferenceStore = koinInject<PreferenceStore>()
  return viewModel(
    key = key,
    factory = SearchViewModel.factory(application, folderListViewModel.videoFolders, audioOnly, preferenceStore),
  )
}

@Composable
private fun MediaSearchContent(
  route: MediaSearchScreen,
  sharedTransitionScope: SharedTransitionScope?,
  animatedVisibilityScope: AnimatedVisibilityScope,
) {
  val audioOnly = route.audioOnly
  val context = LocalContext.current
  val backStack = LocalBackStack.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val searchViewModel = rememberMediaSearchViewModel(audioOnly, key = "MediaSearchViewModel:${if (audioOnly) "music" else "home"}")
  val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
  val recentSearches by searchViewModel.recentSearches.collectAsStateWithLifecycle()
  val mediaLayoutMode by koinInject<BrowserPreferences>().folderViewFolderLayoutMode.collectAsState()

  // The text field reads this synchronous state so IME edits are never overwritten by a value that
  // is still travelling through a StateFlow collector; every edit is forwarded to the ViewModel.
  var query by rememberSaveable { mutableStateOf(searchViewModel.searchQuery.value) }
  val updateQuery: (String) -> Unit = { text ->
    query = text
    searchViewModel.onQueryChange(text)
  }
  LaunchedEffect(searchViewModel) { searchViewModel.onQueryChange(query) }

  // Leaving Search (not merely covering it with a result) ends the session.
  DisposableEffect(searchViewModel) {
    onDispose { if (route !in backStack) searchViewModel.reset() }
  }

  val focusRequester = remember { FocusRequester() }
  var hasAutoFocused by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (hasAutoFocused) return@LaunchedEffect
    // Raise the keyboard once the morph has landed, so IME insets don't resize the layout mid-flight.
    val transition = animatedVisibilityScope.transition
    snapshotFlow {
      transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    }.first { it }
    runCatching { focusRequester.requestFocus() }
    hasAutoFocused = true
  }

  val openFolder: (VideoFolder) -> Unit = { folder ->
    keyboardController?.hide()
    searchViewModel.commitQuery(query)
    backStack.navigateTo(VideoListScreen(folder.bucketId, folder.name, isAudio = audioOnly))
  }
  val playMedia: (Video) -> Unit = { video ->
    keyboardController?.hide()
    searchViewModel.commitQuery(query)
    MediaUtils.playFile(video, context)
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
  ) {
    InlineSearchBar(
      query = query,
      onQueryChange = updateQuery,
      onSearch = { submitted -> searchViewModel.commitQuery(submitted) },
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .then(rememberSearchMorphModifier(sharedTransitionScope, animatedVisibilityScope)),
      inputFieldModifier = Modifier.focusRequester(focusRequester),
      placeholder = {
        Text(
          stringResource(if (audioOnly) R.string.ui_search_folders_and_songs else R.string.ui_search_folders_and_videos),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      leadingIcon = { Icon(Icons.RoundedFilled.Search, contentDescription = null) },
      trailingIcon = {
        val hasQuery = query.isNotEmpty()
        IconButton(
          onClick = {
            if (hasQuery) {
              updateQuery("")
              runCatching { focusRequester.requestFocus() }
            } else {
              keyboardController?.hide()
              backStack.popSafely()
            }
          },
        ) {
          Icon(
            Icons.RoundedFilled.Close,
            contentDescription = stringResource(if (hasQuery) R.string.ui_search_clear_query else R.string.ui_search_close),
          )
        }
      },
      shape =
        animatedVisibilityScope.rememberMorphingCornerShape(
          visible = SearchBarCornerRadius,
          hidden = SearchButtonCornerRadius,
        ),
      tonalElevation = 6.dp,
    )

    MediaSearchBody(
      query = query,
      uiState = uiState,
      audioOnly = audioOnly,
      mediaLayoutMode = mediaLayoutMode,
      bottomPadding = 0.dp,
      onFolderClick = openFolder,
      onMediaClick = playMedia,
      modifier =
        Modifier
          .weight(1f)
          .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
      idleContent = {
        RecentSearches(
          entries = recentSearches,
          audioOnly = audioOnly,
          onSelect = { entry ->
            updateQuery(entry)
            searchViewModel.commitQuery(entry)
          },
          onRemove = searchViewModel::removeRecentSearch,
          onClearAll = searchViewModel::clearRecentSearches,
        )
      },
    )
  }
}

/**
 * Renders a [SearchUiState] for [query]. A blank query always shows [idleContent], so "No results
 * found" can only appear once the user has typed something that matches nothing.
 */
@Composable
internal fun MediaSearchBody(
  query: String,
  uiState: SearchUiState,
  audioOnly: Boolean,
  mediaLayoutMode: MediaLayoutMode,
  bottomPadding: Dp,
  onFolderClick: (VideoFolder) -> Unit,
  onMediaClick: (Video) -> Unit,
  modifier: Modifier = Modifier,
  idleContent: @Composable () -> Unit = {},
) {
  Box(modifier = modifier.fillMaxSize()) {
    when {
      query.isBlank() -> idleContent()
      uiState is SearchUiState.Success ->
        MediaSearchResults(
          results = uiState.results,
          mediaLayoutMode = mediaLayoutMode,
          bottomPadding = bottomPadding,
          onFolderClick = onFolderClick,
          onMediaClick = onMediaClick,
        )
      uiState is SearchUiState.Empty ->
        EmptyState(
          icon = Icons.RoundedFilled.Search,
          title = stringResource(R.string.ui_no_results_found),
          message = stringResource(if (audioOnly) R.string.ui_search_no_matching_audio else R.string.ui_search_no_matching_videos),
          modifier = Modifier.fillMaxSize(),
        )
      uiState is SearchUiState.Loading ->
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      // Initial with a non-blank query: the debounce is still pending; keep the area quiet.
      else -> Unit
    }
  }
}

@Composable
private fun RecentSearches(
  entries: List<String>,
  audioOnly: Boolean,
  onSelect: (String) -> Unit,
  onRemove: (String) -> Unit,
  onClearAll: () -> Unit,
) {
  if (entries.isEmpty()) {
    EmptyState(
      icon = Icons.RoundedFilled.Search,
      title = stringResource(R.string.ui_search_library_title),
      message = stringResource(if (audioOnly) R.string.ui_search_library_audio_message else R.string.ui_search_library_videos_message),
      modifier = Modifier.fillMaxSize(),
    )
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
    item(key = "header", contentType = "header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.ui_search_history),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = onClearAll) { Text(stringResource(R.string.ui_clear_history)) }
      }
    }
    items(items = entries, key = { it }, contentType = { "recent_search" }) { entry ->
      Row(
        modifier =
          Modifier
            .animateItem()
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onSelect(entry) }
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.RoundedFilled.History,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = entry,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        IconButton(onClick = { onRemove(entry) }) {
          Icon(
            Icons.RoundedFilled.Close,
            contentDescription = stringResource(R.string.ui_search_remove_recent),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
