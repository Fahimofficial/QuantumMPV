/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.App
import com.quantummpv.app.R
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.LocalNavigationBarHeight
import com.quantummpv.app.ui.browser.components.BrowserTopBar
import com.quantummpv.app.ui.browser.components.rememberSwipePlaybackInfo
import com.quantummpv.app.ui.browser.dialogs.DeleteConfirmationDialog
import com.quantummpv.app.ui.browser.insights.LibraryInsightsScreen
import com.quantummpv.app.ui.browser.mainTabSearchAction
import com.quantummpv.app.ui.browser.networkstreaming.NetworkStreamingScreen
import com.quantummpv.app.ui.browser.playlist.PlaylistDetailScreen
import com.quantummpv.app.ui.browser.playlist.PlaylistScreen
import com.quantummpv.app.ui.browser.playlist.PlaylistViewModel
import com.quantummpv.app.ui.browser.recentlyplayed.RecentlyPlayedItem
import com.quantummpv.app.ui.browser.recentlyplayed.RecentlyPlayedScreen
import com.quantummpv.app.ui.browser.recentlyplayed.RecentlyPlayedViewModel
import com.quantummpv.app.ui.browser.search.MediaSearchScreen
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.preferences.PreferencesScreen
import com.quantummpv.app.ui.theme.wallpaperAwareBackgroundColor
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.navigateTo
import com.quantummpv.app.ui.utils.rememberAppHaptics
import com.quantummpv.app.utils.media.MediaUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

private fun playlistDeleteAction(
  playlist: PlaylistEntity,
  isProtectedPlaylist: (PlaylistEntity) -> Boolean,
  onDelete: (PlaylistEntity) -> Unit,
): (() -> Unit)? = if (isProtectedPlaylist(playlist)) null else ({ onDelete(playlist) })

private const val SHELF_LIMIT = 15
internal val SHELF_VIDEO_CARD_WIDTH = 214.dp
internal val SHELF_AUDIO_CARD_WIDTH = 120.dp

/**
 * Personal hub that folds Recents and Playlists into one bottom-navigation tab.
 *
 * Adapted from mpvRx's Profile screen. mpvRx's Snapshots shelf and watch-statistics bento depend on
 * subsystems QuantumMPV does not have (frame capture library, watch-session recording), so their
 * slot links to QuantumMPV's own Library Insights instead.
 */
@Serializable
object ProfileScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val application = context.applicationContext as App
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val appearancePreferences = koinInject<AppearancePreferences>()
    val advancedPreferences = koinInject<AdvancedPreferences>()
    val enableRecentlyPlayed by advancedPreferences.enableRecentlyPlayed.collectAsState()
    val profileName by appearancePreferences.profileName.collectAsState()
    val profileImagePath by appearancePreferences.profileImagePath.collectAsState()
    val navigationBarHeight = LocalNavigationBarHeight.current
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val horizontalShelfPadding = if (isTablet) 24.dp else 16.dp

    // Both view models observe Room, so the shelves stay current without manual refreshes.
    val recentsViewModel: RecentlyPlayedViewModel = viewModel(factory = RecentlyPlayedViewModel.factory(application))
    val playlistViewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.factory(application))
    val recentItems by recentsViewModel.recentItems.collectAsState()
    val playlists by playlistViewModel.playlistsWithCount.collectAsState()

    val recentShelf = remember(recentItems) { recentItems.take(SHELF_LIMIT) }
    val recentVideos =
      remember(recentShelf) { recentShelf.filterIsInstance<RecentlyPlayedItem.VideoItem>().map { it.video } }
    val playbackInfo = rememberSwipePlaybackInfo(recentVideos)

    var activeVideoItem by remember { mutableStateOf<RecentlyPlayedItem.VideoItem?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var profileSaveFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val missingFileMessage = stringResource(R.string.ui_recent_file_no_longer_exists)
    val heroCollapseProgress by remember {
      derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
      }
    }
    // The dock's Search opens global media search here, with the same morph Home uses.
    val topBarSearchAction =
      mainTabSearchAction(isSearchActive = false, opensScreen = true) {
        backStack.navigateTo(MediaSearchScreen())
      }

    fun playRecent(video: Video) {
      scope.launch {
        val playable = recentsViewModel.resolvePlayableRecentVideo(video)
        if (playable != null) {
          MediaUtils.playFile(playable, context, "recently_played")
        } else {
          Toast.makeText(context, missingFileMessage, Toast.LENGTH_SHORT).show()
        }
      }
    }

    Scaffold(
      containerColor = wallpaperAwareBackgroundColor(),
      topBar = {
        BrowserTopBar(
          title = stringResource(R.string.ui_profile),
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onCancelSelection = { },
          onSearchClick = topBarSearchAction,
          onSettingsClick = { backStack.navigateTo(PreferencesScreen) },
        )
      },
    ) { paddingValues ->
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(bottom = navigationBarHeight + 16.dp),
      ) {
        item(key = "profile_overview") {
          ProfileOverviewCard(
            name = profileName,
            recentCount = recentItems.size,
            playlistCount = playlists.size,
            collapseProgress = { heroCollapseProgress },
            onEditProfile = {
              haptics.tick()
              showProfileDialog = true
            },
            onRecents = { backStack.navigateTo(RecentlyPlayedScreen) },
            onPlaylists = { backStack.navigateTo(PlaylistScreen) },
            onInsights = { backStack.navigateTo(LibraryInsightsScreen) },
            onNetwork = { backStack.navigateTo(NetworkStreamingScreen) },
          )
        }

        item(key = "recents_header") {
          SectionHeader(
            title = stringResource(R.string.pref_advanced_enable_recently_played_title),
            count = if (enableRecentlyPlayed) recentItems.size else null,
            onViewAll = { backStack.navigateTo(RecentlyPlayedScreen) },
          )
        }
        item(key = "recents_shelf") {
          when {
            !enableRecentlyPlayed ->
              ShelfEmptyCard(
                icon = Icons.RoundedFilled.History,
                title = stringResource(R.string.ui_recently_played_disabled),
                message = stringResource(R.string.ui_recently_played_disabled_message),
              )

            recentShelf.isEmpty() ->
              ShelfEmptyCard(
                icon = Icons.RoundedFilled.History,
                title = stringResource(R.string.ui_no_recently_played_videos),
                message = stringResource(R.string.profile_recents_empty_message),
              )

            else ->
              LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalShelfPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(
                  recentShelf,
                  key = { item ->
                    when (item) {
                      is RecentlyPlayedItem.VideoItem -> "video_${item.video.path}"
                      is RecentlyPlayedItem.PlaylistItem -> "playlist_${item.playlist.id}"
                    }
                  },
                ) { item ->
                  when (item) {
                    is RecentlyPlayedItem.VideoItem -> {
                      val info = playbackInfo[item.video.path]
                      RecentVideoCard(
                        video = item.video,
                        progress = info?.progressPercentage?.takeUnless { info.isWatched },
                        onClick = { playRecent(item.video) },
                        onLongClick = {
                          haptics.pickup()
                          activeVideoItem = item
                        },
                      )
                    }

                    is RecentlyPlayedItem.PlaylistItem ->
                      ShelfPlaylistCard(
                        playlist = item.playlist,
                        itemCount = item.videoCount,
                        onOpen = { backStack.navigateTo(PlaylistDetailScreen(item.playlist.id)) },
                        onDelete = playlistDeleteAction(item.playlist, playlistViewModel::isProtectedPlaylist) { playlistToDelete = it },
                      )
                  }
                }
              }
          }
        }

        item(key = "playlists_header") {
          SectionHeader(
            title = stringResource(R.string.ui_playlists),
            count = playlists.size,
            onViewAll = { backStack.navigateTo(PlaylistScreen) },
          )
        }
        item(key = "playlists_shelf") {
          if (playlists.isEmpty()) {
            ShelfEmptyCard(
              icon = Icons.RoundedFilled.PlaylistPlay,
              title = stringResource(R.string.ui_no_playlists_yet),
              message = stringResource(R.string.profile_playlists_empty_message),
            )
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = horizontalShelfPadding),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(playlists.take(SHELF_LIMIT), key = { it.playlist.id }) { entry ->
                ShelfPlaylistCard(
                  playlist = entry.playlist,
                  itemCount = entry.itemCount,
                  onOpen = { backStack.navigateTo(PlaylistDetailScreen(entry.playlist.id)) },
                  onDelete = playlistDeleteAction(entry.playlist, playlistViewModel::isProtectedPlaylist) { playlistToDelete = it },
                )
              }
            }
          }
        }
      }
    }

    activeVideoItem?.let { item ->
      RecentVideoActionsSheet(
        video = item.video,
        onDismiss = { activeVideoItem = null },
        onPlay = {
          activeVideoItem = null
          playRecent(item.video)
        },
        onShare = {
          activeVideoItem = null
          MediaUtils.shareVideos(context, listOf(item.video))
        },
        onRemove = {
          activeVideoItem = null
          scope.launch { recentsViewModel.deleteVideosFromHistory(listOf(item.video)) }
        },
      )
    }

    if (showProfileDialog) {
      ProfileEditDialog(
        initialName = profileName,
        hasPhoto = profileImagePath.isNotBlank(),
        isSaving = isSavingProfile,
        showSaveError = profileSaveFailed,
        onDismiss = { if (!isSavingProfile) showProfileDialog = false },
        onSave = { name, newPhoto, removePhoto ->
          profileSaveFailed = false
          isSavingProfile = true
          application.applicationScope.launch(Dispatchers.Main.immediate) {
            var newAvatarPath: String? = null
            var avatarPreferenceUpdated = false
            try {
              if (newPhoto != null || removePhoto) {
                // Retain the previous file until the new path is durably stored.
                val avatarPath = withContext(Dispatchers.IO) { storeProfileAvatar(context, newPhoto) }
                newAvatarPath = avatarPath
                appearancePreferences.profileImagePath.set(avatarPath)
                avatarPreferenceUpdated = true
              }
              appearancePreferences.profileName.set(name.trim())
              if (newPhoto != null || removePhoto) {
                withContext(Dispatchers.IO) { deleteStoredProfileAvatars(context, keepPath = newAvatarPath.orEmpty()) }
              }
              showProfileDialog = false
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              if (!avatarPreferenceUpdated) {
                newAvatarPath?.takeIf(String::isNotBlank)?.let { path ->
                  withContext(Dispatchers.IO) { File(path).delete() }
                }
              }
              profileSaveFailed = true
            } finally {
              isSavingProfile = false
            }
          }
        },
      )
    }

    playlistToDelete?.let { target ->
      DeleteConfirmationDialog(
        isOpen = true,
        onDismiss = { playlistToDelete = null },
        onConfirm = {
          playlistToDelete = null
          scope.launch { playlistViewModel.deletePlaylist(target) }
        },
        itemCount = 1,
        itemType = "playlist",
        itemNames = listOf(target.name),
      )
    }
  }
}
