/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.browser.jellyfin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantummpv.app.data.jellyfin.JellyfinClient
import com.quantummpv.app.domain.jellyfin.JellyfinItem
import com.quantummpv.app.domain.jellyfin.JellyfinServer
import com.quantummpv.app.presentation.components.RemoteImage
import com.quantummpv.app.ui.icons.AppIcon
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantummpv.app.ui.browser.music.SharedCompactTrackGridSection
import com.quantummpv.app.ui.browser.music.SharedMusicCarouselSection
import com.quantummpv.app.ui.browser.music.SharedMusicGridCard
import com.quantummpv.app.ui.browser.music.SharedMusicTrackListItem
import com.quantummpv.app.ui.player.PlaybackSession

@Composable
fun JellyfinMusicView(
  uiState: JellyfinUiState,
  server: JellyfinServer,
  pagerState: PagerState,
  visibleTabs: List<JellyfinMusicTab>,
  onTabSelected: (JellyfinMusicTab) -> Unit,
  onItemClick: (JellyfinItem) -> Unit,
  onItemLongClick: (JellyfinItem) -> Unit,
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
) {
  HorizontalPager(
    state = pagerState,
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    beyondViewportPageCount = 1,
    key = { page -> visibleTabs.getOrNull(page) ?: page },
  ) { page ->
    val tab = visibleTabs.getOrNull(page) ?: JellyfinMusicTab.HOME
    Box(
      modifier = Modifier.fillMaxSize(),
    ) {
      when (tab) {
        JellyfinMusicTab.HOME -> {
          if (uiState.isMusicLoading && uiState.musicJumpBackIn.isEmpty() && uiState.musicPlaylists.isEmpty() && uiState.musicRecentlyPlayedAlbums.isEmpty() && uiState.musicArtistsToExplore.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            JellyfinMusicHomeContent(
              uiState = uiState,
              server = server,
              onTabSelected = onTabSelected,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }
        JellyfinMusicTab.TRACKS -> {
          if (uiState.isLoading && uiState.musicTracks.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            JellyfinTracksList(
              tracks = uiState.musicTracks,
              server = server,
              onTrackClick = onItemClick,
              onTrackLongClick = onItemLongClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }
        JellyfinMusicTab.ALBUMS -> {
          if (uiState.isLoading && uiState.musicAlbums.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            JellyfinMusicGrid(
              items = uiState.musicAlbums,
              server = server,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }
        JellyfinMusicTab.ARTISTS -> {
          if (uiState.isLoading && uiState.musicArtists.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            JellyfinArtistsGrid(
              artists = uiState.musicArtists,
              server = server,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }
        JellyfinMusicTab.PLAYLISTS -> {
          if (uiState.isLoading && uiState.musicPlaylists.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            JellyfinMusicGrid(
              items = uiState.musicPlaylists,
              server = server,
              onItemClick = onItemClick,
              onItemLongClick = onItemLongClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun JellyfinMusicHomeContent(
  uiState: JellyfinUiState,
  server: JellyfinServer,
  onTabSelected: (JellyfinMusicTab) -> Unit,
  onItemClick: (JellyfinItem) -> Unit,
  onItemLongClick: (JellyfinItem) -> Unit,
  navigationBarHeight: Dp,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = 16.dp, bottom = navigationBarHeight + 84.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {

    if (uiState.musicJumpBackIn.isNotEmpty()) {
      item(key = "jump_back_in") {
        JellyfinCompactTrackGridSection(
          title = "Jump back in",
          tracks = uiState.musicJumpBackIn,
          server = server,
          onTrackClick = onItemClick,
        )
      }
    }

    if (uiState.musicPlaylists.isNotEmpty()) {
      item(key = "music_playlists_row") {
        JellyfinPlaylistsRowSection(
          title = "Playlists",
          playlists = uiState.musicPlaylists,
          server = server,
          onPlaylistClick = onItemClick,
          onPlaylistLongClick = onItemLongClick,
          onSeeAllClick = { onTabSelected(JellyfinMusicTab.PLAYLISTS) },
        )
      }
    }

    if (uiState.musicRecentlyPlayedAlbums.isNotEmpty()) {
      item(key = "recently_played_albums") {
        JellyfinMusicAlbumRowSection(
          title = "Recently played album",
          albums = uiState.musicRecentlyPlayedAlbums,
          server = server,
          onAlbumClick = onItemClick,
          onAlbumLongClick = onItemLongClick,
        )
      }
    }

    if (uiState.musicArtistsToExplore.isNotEmpty()) {
      item(key = "artists_to_explore") {
        JellyfinArtistsRowSection(
          title = "Artist to explore",
          artists = uiState.musicArtistsToExplore,
          server = server,
          onArtistClick = onItemClick,
          onArtistLongClick = onItemLongClick,
        )
      }
    }
  }
}



@Composable
fun JellyfinCompactTrackGridSection(
  title: String,
  tracks: List<JellyfinItem>,
  server: JellyfinServer,
  onTrackClick: (JellyfinItem) -> Unit,
  modifier: Modifier = Modifier,
  onSeeAllClick: (() -> Unit)? = null,
) {
  SharedCompactTrackGridSection(
    title = title,
    tracks = tracks,
    getId = { it.id },
    getTitle = { it.name },
    getSubtitle = { it.seriesName ?: it.overview ?: "" },
    getArtworkUrl = { track ->
      if (!track.primaryImageTag.isNullOrBlank()) {
        JellyfinClient.getImageUrl(
          serverUrl = server.serverUrl,
          itemId = track.id,
          imageTag = track.primaryImageTag,
          maxWidth = 200,
          token = server.accessToken,
        )
      } else null
    },
    onTrackClick = onTrackClick,
    onSeeAllClick = onSeeAllClick,
    modifier = modifier,
  )
}

@Composable
fun JellyfinPlaylistsRowSection(
  title: String,
  playlists: List<JellyfinItem>,
  server: JellyfinServer,
  onPlaylistClick: (JellyfinItem) -> Unit,
  onPlaylistLongClick: (JellyfinItem) -> Unit,
  modifier: Modifier = Modifier,
  onSeeAllClick: (() -> Unit)? = null,
) {
  SharedMusicCarouselSection(
    title = title,
    items = playlists,
    getId = { it.id },
    getTitle = { it.name },
    getSubtitle = { it.seriesName ?: it.overview ?: "" },
    getArtworkUrl = { playlist ->
      if (!playlist.primaryImageTag.isNullOrBlank()) {
        JellyfinClient.getImageUrl(
          serverUrl = server.serverUrl,
          itemId = playlist.id,
          imageTag = playlist.primaryImageTag,
          maxWidth = 300,
          token = server.accessToken,
        )
      } else null
    },
    fallbackIcon = Icons.RoundedFilled.QueueMusic,
    onClick = onPlaylistClick,
    onLongClick = onPlaylistLongClick,
    onSeeAllClick = onSeeAllClick,
    cardWidth = 140.dp,
    modifier = modifier,
  )
}

@Composable
fun JellyfinMusicAlbumRowSection(
  title: String,
  albums: List<JellyfinItem>,
  server: JellyfinServer,
  onAlbumClick: (JellyfinItem) -> Unit,
  onAlbumLongClick: (JellyfinItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedMusicCarouselSection(
    title = title,
    items = albums,
    getId = { it.id },
    getTitle = { it.name },
    getSubtitle = { it.seriesName ?: it.overview ?: "" },
    getArtworkUrl = { album ->
      if (!album.primaryImageTag.isNullOrBlank()) {
        JellyfinClient.getImageUrl(
          serverUrl = server.serverUrl,
          itemId = album.id,
          imageTag = album.primaryImageTag,
          maxWidth = 300,
          token = server.accessToken,
        )
      } else null
    },
    onClick = onAlbumClick,
    onLongClick = onAlbumLongClick,
    cardWidth = 140.dp,
    modifier = modifier,
  )
}

@Composable
fun JellyfinArtistsRowSection(
  title: String,
  artists: List<JellyfinItem>,
  server: JellyfinServer,
  onArtistClick: (JellyfinItem) -> Unit,
  onArtistLongClick: (JellyfinItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  SharedMusicCarouselSection(
    title = title,
    items = artists,
    getId = { it.id },
    getTitle = { it.name },
    getSubtitle = { "" },
    getArtworkUrl = { artist ->
      if (!artist.primaryImageTag.isNullOrBlank()) {
        JellyfinClient.getImageUrl(
          serverUrl = server.serverUrl,
          itemId = artist.id,
          imageTag = artist.primaryImageTag,
          maxWidth = 300,
          token = server.accessToken,
        )
      } else null
    },
    isCircular = true,
    cardWidth = 130.dp,
    fallbackIcon = Icons.RoundedFilled.Person,
    onClick = onArtistClick,
    onLongClick = onArtistLongClick,
    modifier = modifier,
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JellyfinMusicCard(
  item: JellyfinItem,
  server: JellyfinServer,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  cardWidth: Dp = 145.dp,
) {
  val imageUrl = remember(server.serverUrl, item.id, item.primaryImageTag, server.accessToken) {
    JellyfinClient.getImageUrl(
      serverUrl = server.serverUrl,
      itemId = item.id,
      imageTag = item.primaryImageTag,
      maxWidth = 300,
      token = server.accessToken,
    )
  }
  val isArtist = item.type == "MusicArtist" || item.type == "Artist" || item.type == "AlbumArtist"
  val subtitle = if (isArtist) "" else (item.seriesName ?: item.overview ?: "")

  SharedMusicGridCard(
    title = item.name,
    subtitle = subtitle,
    artworkUrl = if (!item.primaryImageTag.isNullOrBlank()) imageUrl else null,
    fallbackIcon = when {
      item.id == "virtual_favorites_playlist" || item.id == "favorites" -> Icons.RoundedFilled.Favorite
      item.type == "MusicArtist" || item.type == "Artist" || item.type == "AlbumArtist" -> Icons.RoundedFilled.Person
      item.type == "Playlist" -> Icons.RoundedFilled.QueueMusic
      else -> Icons.RoundedFilled.Audiotrack
    },
    isCircular = item.type == "MusicArtist" || item.type == "Artist" || item.type == "AlbumArtist",
    cardWidth = cardWidth,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
  )
}

@Composable
fun JellyfinMusicGrid(
  items: List<JellyfinItem>,
  server: JellyfinServer,
  onItemClick: (JellyfinItem) -> Unit,
  onItemLongClick: (JellyfinItem) -> Unit,
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navigationBarHeight + 84.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(items, key = { it.id }) { item ->
      JellyfinMusicCard(
        item = item,
        server = server,
        onClick = { onItemClick(item) },
        onLongClick = { onItemLongClick(item) },
      )
    }
  }
}

@Composable
fun JellyfinArtistsGrid(
  artists: List<JellyfinItem>,
  server: JellyfinServer,
  onItemClick: (JellyfinItem) -> Unit,
  onItemLongClick: (JellyfinItem) -> Unit,
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 130.dp),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navigationBarHeight + 84.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(artists, key = { it.id }) { artist ->
      JellyfinMusicCard(
        item = artist,
        server = server,
        onClick = { onItemClick(artist) },
        onLongClick = { onItemLongClick(artist) },
        cardWidth = 130.dp,
      )
    }
  }
}

@Composable
fun JellyfinTracksList(
  tracks: List<JellyfinItem>,
  server: JellyfinServer,
  onTrackClick: (JellyfinItem) -> Unit,
  onTrackLongClick: (JellyfinItem) -> Unit,
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val queueState by PlaybackSession.queue.collectAsStateWithLifecycle()
  val currentSessionItem = queueState.currentItem

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = navigationBarHeight + 84.dp),
  ) {
    items(tracks, key = { it.id }) { track ->
      val imageUrl = remember(server.serverUrl, track.id, track.primaryImageTag, server.accessToken) {
        JellyfinClient.getImageUrl(
          serverUrl = server.serverUrl,
          itemId = track.id,
          imageTag = track.primaryImageTag,
          maxWidth = 200,
          token = server.accessToken,
        )
      }

      val isPlaying = remember(currentSessionItem, track.id) {
        if (currentSessionItem == null || track.id.isBlank()) false
        else {
          val orig = currentSessionItem.originalUri
          val play = currentSessionItem.playableUri
          orig.contains(track.id, ignoreCase = true) || play.contains(track.id, ignoreCase = true)
        }
      }
      val subtitle = track.seriesName ?: track.overview ?: ""

      SharedMusicTrackListItem(
        title = track.name,
        subtitle = subtitle,
        artworkUrl = if (!track.primaryImageTag.isNullOrBlank()) imageUrl else null,
        durationSeconds = track.durationSeconds,
        isPlaying = isPlaying,
        onClick = { onTrackClick(track) },
        onLongClick = { onTrackLongClick(track) },
      )
    }
  }
}
