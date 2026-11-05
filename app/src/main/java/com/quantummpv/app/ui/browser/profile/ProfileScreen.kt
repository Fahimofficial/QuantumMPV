/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quantummpv.app.App
import com.quantummpv.app.R
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.thumbnail.ThumbnailRepository
import com.quantummpv.app.preferences.AdvancedPreferences
import com.quantummpv.app.preferences.AppearancePreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.browser.LocalNavigationBarHeight
import com.quantummpv.app.ui.browser.cards.PlaylistCard
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
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import com.quantummpv.app.ui.player.controls.components.tvFocusHighlight
import com.quantummpv.app.ui.preferences.PreferencesScreen
import com.quantummpv.app.ui.theme.AppShapeScale
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

private const val SHELF_LIMIT = 15
private val SHELF_VIDEO_CARD_WIDTH = 214.dp
private val SHELF_AUDIO_CARD_WIDTH = 120.dp

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

    fun deleteAction(playlist: PlaylistEntity): (() -> Unit)? =
      if (playlistViewModel.isProtectedPlaylist(playlist)) null else ({ playlistToDelete = playlist })

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
                        onDelete = deleteAction(item.playlist),
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
                  onDelete = deleteAction(entry.playlist),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfPlaylistCard(
  playlist: PlaylistEntity,
  itemCount: Int,
  onOpen: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  // QuantumMPV's PlaylistCard has no delete slot, so long-press offers the delete action.
  PlaylistCard(
    playlist = playlist,
    itemCount = itemCount,
    onClick = onOpen,
    onLongClick = { onDelete?.invoke() },
    onThumbClick = onOpen,
    modifier = Modifier.width(SHELF_VIDEO_CARD_WIDTH),
    isGridMode = true,
  )
}

/** The user's avatar, or the account glyph when none is set. */
@Composable
internal fun ProfileAvatar(
  size: Dp,
  tint: Color,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color? = null,
) {
  val path by koinInject<AppearancePreferences>().profileImagePath.collectAsState()
  val avatar = rememberProfileAvatar(path)
  when {
    avatar != null ->
      Image(
        bitmap = avatar,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape),
      )

    containerColor != null ->
      Surface(shape = CircleShape, color = containerColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.RoundedFilled.Person,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.54f),
          )
        }
      }

    else ->
      Icon(Icons.RoundedFilled.AccountCircle, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
  }
}

@Composable
private fun rememberProfileAvatar(path: String): ImageBitmap? {
  val avatar by produceState<ImageBitmap?>(initialValue = null, path) {
    value =
      if (path.isBlank()) {
        null
      } else {
        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
      }
  }
  return avatar
}

private data class ProfileShortcutDestination(
  val icon: AppIcon,
  val label: String,
  val count: Int?,
  val accent: Color,
  val onClick: () -> Unit,
)

@Composable
private fun ProfileOverviewCard(
  name: String,
  recentCount: Int,
  playlistCount: Int,
  collapseProgress: () -> Float,
  onEditProfile: () -> Unit,
  onRecents: () -> Unit,
  onPlaylists: () -> Unit,
  onInsights: () -> Unit,
  onNetwork: () -> Unit,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  val editLabel = stringResource(R.string.profile_edit)
  val horizontalPadding = if (isTablet) 24.dp else 16.dp

  Column(
    modifier = Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        contentAlignment = Alignment.BottomEnd,
        // Read in the layer phase only, so scrolling never recomposes the hero.
        modifier =
          Modifier.graphicsLayer {
            val progress = collapseProgress()
            val scale = 1f - 0.25f * progress
            scaleX = scale
            scaleY = scale
            alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
          },
      ) {
        ProfileAvatar(
          size = if (isTablet) 116.dp else 96.dp,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentDescription = null,
        )
        // Small visual badge with an independent 48 dp touch target.
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .offset(x = 6.dp, y = 6.dp)
              .tvFocusHighlight(CircleShape)
              .clip(CircleShape)
              .clickable(onClick = onEditProfile),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
            shadowElevation = 2.dp,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.RoundedFilled.Edit, contentDescription = editLabel, modifier = Modifier.size(14.dp))
            }
          }
        }
      }

      Text(
        text = name.ifBlank { stringResource(R.string.ui_profile) },
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    val shortcuts =
      listOf(
        ProfileShortcutDestination(
          Icons.RoundedFilled.History,
          stringResource(R.string.ui_recents),
          recentCount,
          MaterialTheme.colorScheme.primary,
          onRecents,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.PlaylistPlay,
          stringResource(R.string.ui_playlists),
          playlistCount,
          MaterialTheme.colorScheme.secondary,
          onPlaylists,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.Insights,
          stringResource(R.string.library_insights_title),
          null,
          MaterialTheme.colorScheme.tertiary,
          onInsights,
        ),
        ProfileShortcutDestination(
          Icons.RoundedFilled.BringYourOwnIp,
          stringResource(R.string.ui_network),
          null,
          MaterialTheme.colorScheme.primary,
          onNetwork,
        ),
      )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val columns =
        when {
          maxWidth < 300.dp -> 1
          maxWidth >= 680.dp -> 4
          else -> 2
        }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.chunked(columns).forEach { rowShortcuts ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowShortcuts.forEach { shortcut ->
              ProfileShortcutTile(shortcut = shortcut, modifier = Modifier.weight(1f))
            }
            repeat(columns - rowShortcuts.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileShortcutTile(
  shortcut: ProfileShortcutDestination,
  modifier: Modifier = Modifier,
) {
  val shape = AppShapeScale.largeIncreased
  Surface(
    onClick = shortcut.onClick,
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = shortcut.accent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    modifier = modifier.heightIn(min = 80.dp).tvFocusHighlight(shape),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = shortcut.accent.copy(alpha = 0.14f),
        contentColor = shortcut.accent,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (shortcut.count != null) {
          Text(
            text = shortcut.count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = shortcut.label,
          style = if (shortcut.count == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        Icons.RoundedFilled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun SectionHeader(
  title: String,
  count: Int?,
  onViewAll: () -> Unit,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = if (isTablet) 24.dp else 16.dp, end = if (isTablet) 16.dp else 8.dp, top = 16.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (count != null && count > 0) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
          Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }
    }
    TextButton(onClick = onViewAll, shape = CircleShape) {
      Text(stringResource(R.string.profile_view_all), style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.width(2.dp))
      Icon(Icons.RoundedFilled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
private fun ShelfEmptyCard(
  icon: AppIcon,
  title: String,
  message: String,
) {
  val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
  Surface(
    shape = AppShapeScale.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 2.dp),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

/** 16:9 frame for video or 1:1 frame for audio plus a caption. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
  title: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  isAudio: Boolean,
  frame: @Composable BoxScope.() -> Unit,
) {
  val cardWidth = if (isAudio) SHELF_AUDIO_CARD_WIDTH else SHELF_VIDEO_CARD_WIDTH
  val cardShape = if (isAudio) RoundedCornerShape(16.dp) else RoundedCornerShape(14.dp)
  Column(
    modifier =
      Modifier
        .width(cardWidth)
        .tvFocusHighlight(cardShape)
        .clip(cardShape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(if (isAudio) 1f else 16f / 9f)
          .clip(cardShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
      content = frame,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun RecentVideoCard(
  video: Video,
  progress: Float?,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val isAudio = video.isAudio
  val widthPx = with(density) { (if (isAudio) SHELF_AUDIO_CARD_WIDTH else SHELF_VIDEO_CARD_WIDTH).roundToPx() }
  val heightPx = if (isAudio) widthPx else widthPx * 9 / 16

  var thumbnail by remember(video.path) {
    mutableStateOf(thumbnailRepository.getThumbnailFromMemory(video, widthPx, heightPx))
  }
  LaunchedEffect(video.path) {
    if (thumbnail == null) thumbnail = thumbnailRepository.getThumbnail(video, widthPx, heightPx)
  }

  ShelfCard(title = video.displayName, onClick = onClick, onLongClick = onLongClick, isAudio = isAudio) {
    RecentVideoFrame(thumbnail, isAudio, video.durationFormatted, progress)
  }
}

@Composable
private fun BoxScope.RecentVideoFrame(
  thumbnail: Bitmap?,
  isAudio: Boolean,
  duration: String,
  progress: Float?,
) {
  val presentation = recentVideoFramePresentation(thumbnail != null, isAudio, duration, progress)
  RecentVideoArtwork(thumbnail, presentation.fallback)
  RecentVideoDuration(presentation.durationLabel)
  RecentVideoProgress(presentation.progress)
}

internal enum class RecentMediaFallback {
  AUDIO,
  VIDEO,
}

internal data class RecentVideoFramePresentation(
  val fallback: RecentMediaFallback?,
  val durationLabel: String?,
  val progress: Float?,
)

/** Maps media and playback state to the visible placeholder, duration badge, and progress bar. */
internal fun recentVideoFramePresentation(
  hasThumbnail: Boolean,
  isAudio: Boolean,
  duration: String,
  progress: Float?,
): RecentVideoFramePresentation =
  RecentVideoFramePresentation(
    fallback = if (hasThumbnail) null else if (isAudio) RecentMediaFallback.AUDIO else RecentMediaFallback.VIDEO,
    durationLabel = duration.takeIf { it.isNotBlank() && it != "--:--" },
    progress = progress?.takeIf { it > 0f },
  )

@Composable
private fun BoxScope.RecentVideoArtwork(
  thumbnail: Bitmap?,
  fallback: RecentMediaFallback?,
) {
  if (thumbnail != null) {
    Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
  } else {
    Icon(
      if (fallback == RecentMediaFallback.AUDIO) Icons.RoundedFilled.Audiotrack else Icons.RoundedFilled.PlayArrow,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.align(Alignment.Center).size(if (fallback == RecentMediaFallback.AUDIO) 36.dp else 32.dp),
    )
  }
}

@Composable
private fun BoxScope.RecentVideoDuration(duration: String?) {
  if (duration != null) {
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color.Black.copy(alpha = 0.75f),
      contentColor = Color.White,
      modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
    ) {
      Text(
        text = duration,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
private fun BoxScope.RecentVideoProgress(progress: Float?) {
  if (progress != null && progress > 0f) {
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
      color = MaterialTheme.colorScheme.primary,
      trackColor = Color.Black.copy(alpha = 0.35f),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentVideoActionsSheet(
  video: Video,
  onDismiss: () -> Unit,
  onPlay: () -> Unit,
  onShare: () -> Unit,
  onRemove: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState =
      rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
      ),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
      Text(
        text = video.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      SheetAction(Icons.RoundedFilled.PlayArrow, stringResource(R.string.ui_play), MaterialTheme.colorScheme.onSurface, onPlay)
      if (!video.path.contains("://")) {
        SheetAction(Icons.RoundedFilled.Share, stringResource(R.string.generic_share), MaterialTheme.colorScheme.onSurface, onShare)
      }
      SheetAction(Icons.RoundedFilled.Delete, stringResource(R.string.profile_remove_from_history), MaterialTheme.colorScheme.error, onRemove)
    }
  }
}

@Composable
private fun SheetAction(
  icon: AppIcon,
  label: String,
  tint: Color,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .clip(AppShapeScale.medium)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditDialog(
  initialName: String,
  hasPhoto: Boolean,
  isSaving: Boolean,
  showSaveError: Boolean,
  onDismiss: () -> Unit,
  onSave: (name: String, newPhoto: Bitmap?, removePhoto: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentPath by koinInject<AppearancePreferences>().profileImagePath.collectAsState()
  val currentAvatar = rememberProfileAvatar(currentPath)
  var name by rememberSaveable { mutableStateOf(initialName) }
  var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var rawCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var removePhoto by remember { mutableStateOf(false) }
  val pendingImage = remember(pendingPhoto) { pendingPhoto?.asImageBitmap() }
  val preview = pendingImage ?: currentAvatar.takeUnless { removePhoto }
  val showsPhoto = pendingPhoto != null || (hasPhoto && !removePhoto)

  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          withContext(Dispatchers.IO) { decodeRawProfileAvatar(context, uri) }?.let { rawCropBitmap = it }
        }
      }
    }

  rawCropBitmap?.let { source ->
    ProfileAvatarCropDialog(
      sourceBitmap = source,
      onDismiss = { rawCropBitmap = null },
      onCropped = { cropped ->
        rawCropBitmap = null
        pendingPhoto = cropped
        removePhoto = false
      },
    )
  }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text(stringResource(R.string.profile_edit)) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Surface(
          onClick = { picker.launch("image/*") },
          enabled = !isSaving,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(96.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (preview != null) {
              Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
              Icon(
                Icons.RoundedFilled.Person,
                contentDescription = stringResource(R.string.profile_choose_photo),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
              )
            }
          }
        }
        // FlowRow wraps the third button instead of squeezing it once a photo exists.
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(enabled = !isSaving, onClick = { picker.launch("image/*") }) {
            Text(stringResource(R.string.profile_choose_photo), maxLines = 1)
          }
          if (showsPhoto) {
            OutlinedButton(
              enabled = !isSaving,
              onClick = {
                scope.launch {
                  val toCrop =
                    pendingPhoto
                      ?: currentPath.takeIf { it.isNotBlank() }?.let { path ->
                        withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                      }
                  if (toCrop != null) rawCropBitmap = toCrop
                }
              },
            ) {
              Text(stringResource(R.string.clip_crop), maxLines = 1)
            }
            OutlinedButton(
              enabled = !isSaving,
              onClick = {
                pendingPhoto = null
                removePhoto = true
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) {
              Text(stringResource(R.string.profile_remove_photo), maxLines = 1)
            }
          }
        }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(40) },
          label = { Text(stringResource(R.string.profile_display_name)) },
          singleLine = true,
          enabled = !isSaving,
          modifier = Modifier.fillMaxWidth(),
        )
        if (showSaveError) {
          Text(
            text = stringResource(R.string.profile_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      TextButton(enabled = !isSaving, onClick = { onSave(name, pendingPhoto, removePhoto) }) {
        Text(stringResource(R.string.ui_save))
      }
    },
    dismissButton = {
      TextButton(enabled = !isSaving, onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
    },
  )
}

@Composable
private fun ProfileAvatarCropDialog(
  sourceBitmap: Bitmap,
  onDismiss: () -> Unit,
  onCropped: (Bitmap) -> Unit,
) {
  val configuration = LocalConfiguration.current
  val density = LocalDensity.current
  val isTablet = configuration.smallestScreenWidthDp >= 600
  // Height cap leaves room for the title, rotate and action rows (~220 dp) so Crop stays reachable in landscape.
  val cropBoxDp =
    minOf(
      configuration.screenWidthDp.dp - 64.dp,
      configuration.screenHeightDp.dp - 220.dp,
      if (isTablet) 360.dp else 280.dp,
    ).coerceAtLeast(120.dp)
  val cropBoxPx = with(density) { cropBoxDp.toPx() }

  var rotation by remember { mutableIntStateOf(0) }
  var userScale by remember { mutableFloatStateOf(1f) }
  var userOffset by remember { mutableStateOf(Offset.Zero) }

  val rotatedBitmap = remember(sourceBitmap, rotation) { rotateBitmap(sourceBitmap, rotation) }
  val rotatedImageBitmap = remember(rotatedBitmap) { rotatedBitmap.asImageBitmap() }
  val baseScale =
    remember(rotatedImageBitmap, cropBoxPx) {
      maxOf(cropBoxPx / rotatedImageBitmap.width, cropBoxPx / rotatedImageBitmap.height)
    }

  fun maxPan(
    extent: Int,
    scale: Float,
  ) = ((extent * baseScale * scale - cropBoxPx) / 2f).coerceAtLeast(0f)

  fun resetTransform() {
    userScale = 1f
    userOffset = Offset.Zero
  }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(if (isTablet) 0.65f else 0.94f),
      shape = AppShapeScale.large,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.clip_crop),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.RoundedFilled.Close, contentDescription = stringResource(R.string.generic_cancel))
          }
        }

        Box(
          modifier =
            Modifier
              .size(cropBoxDp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black)
              .pointerInput(rotatedImageBitmap, cropBoxPx) {
                detectTransformGestures { _, pan, zoom, _ ->
                  userScale = (userScale * zoom).coerceIn(1f, 5f)
                  val maxX = maxPan(rotatedImageBitmap.width, userScale)
                  val maxY = maxPan(rotatedImageBitmap.height, userScale)
                  userOffset =
                    Offset(
                      (userOffset.x + pan.x).coerceIn(-maxX, maxX),
                      (userOffset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
              },
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = rotatedImageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier
                .size(
                  width = with(density) { (rotatedImageBitmap.width * baseScale).toDp() },
                  height = with(density) { (rotatedImageBitmap.height * baseScale).toDp() },
                ).graphicsLayer {
                  scaleX = userScale
                  scaleY = userScale
                  translationX = userOffset.x
                  translationY = userOffset.y
                },
          )
          Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f
            val mask =
              Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
              }
            drawPath(mask, color = Color.Black.copy(alpha = 0.60f))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = radius - 1f, center = center, style = Stroke(width = 2.dp.toPx()))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
          CropToolButton(Icons.RoundedFilled.RotateLeft, stringResource(R.string.profile_rotate_left)) {
            rotation = (rotation + 270) % 360
            resetTransform()
          }
          CropToolButton(Icons.RoundedFilled.RotateRight, stringResource(R.string.profile_rotate_right)) {
            rotation = (rotation + 90) % 360
            resetTransform()
          }
          CropToolButton(Icons.RoundedFilled.Refresh, stringResource(R.string.generic_reset)) {
            rotation = 0
            resetTransform()
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
          Button(
            onClick = {
              onCropped(performCrop(sourceBitmap, rotation, userScale, userOffset, cropBoxPx))
            },
          ) {
            Text(stringResource(R.string.clip_crop))
          }
        }
      }
    }
  }
}

@Composable
private fun CropToolButton(
  icon: AppIcon,
  label: String,
  onClick: () -> Unit,
) {
  FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
  }
}
