/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.navidrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantummpv.app.domain.navidrome.NavidromeAlbum
import com.quantummpv.app.domain.navidrome.NavidromeArtist
import com.quantummpv.app.domain.navidrome.NavidromeMusicTab
import com.quantummpv.app.domain.navidrome.NavidromePlaylist
import com.quantummpv.app.domain.navidrome.NavidromeServer
import com.quantummpv.app.domain.navidrome.NavidromeSong
import com.quantummpv.app.presentation.components.RemoteImage
import com.quantummpv.app.repository.NavidromeRepository
import com.quantummpv.app.ui.browser.music.SharedCompactTrackGridSection
import com.quantummpv.app.ui.browser.music.SharedMusicCarouselSection
import com.quantummpv.app.ui.browser.music.SharedMusicGridCard
import com.quantummpv.app.ui.browser.music.SharedMusicTrackListItem
import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons
import org.koin.compose.koinInject

@Composable
fun NavidromeMusicView(
  uiState: NavidromeUiState,
  server: NavidromeServer,
  pagerState: PagerState,
  visibleTabs: List<NavidromeMusicTab>,
  onTabSelected: (NavidromeMusicTab) -> Unit,
  onSongClick: (NavidromeSong) -> Unit,
  onAlbumClick: (NavidromeAlbum) -> Unit,
  onArtistClick: (NavidromeArtist) -> Unit,
  onPlaylistClick: (NavidromePlaylist) -> Unit,
  onToggleFavorite: (NavidromeSong) -> Unit,
  navigationBarHeight: Dp,
  modifier: Modifier = Modifier,
) {
  val navidromeRepository = koinInject<NavidromeRepository>()

  HorizontalPager(
    state = pagerState,
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    beyondViewportPageCount = 1,
    key = { page -> visibleTabs.getOrNull(page) ?: page },
  ) { page ->
    val tab = visibleTabs.getOrNull(page) ?: NavidromeMusicTab.HOME
    Box(modifier = Modifier.fillMaxSize()) {
      when (tab) {
        NavidromeMusicTab.HOME -> {
          if (uiState.isLoading && uiState.jumpBackIn.isEmpty() && uiState.playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          } else {
            NavidromeHomeContent(
              uiState = uiState,
              server = server,
              navidromeRepository = navidromeRepository,
              onTabSelected = onTabSelected,
              onSongClick = onSongClick,
              onAlbumClick = onAlbumClick,
              onArtistClick = onArtistClick,
              onPlaylistClick = onPlaylistClick,
              navigationBarHeight = navigationBarHeight,
            )
          }
        }

        NavidromeMusicTab.TRACKS -> {
          if (uiState.isLoading && uiState.tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(top = 8.dp, bottom = navigationBarHeight + 84.dp),
            ) {
              items(uiState.tracks, key = { it.id }) { song ->
                SharedMusicTrackListItem(
                  title = song.title,
                  subtitle = song.artist,
                  durationSeconds = song.durationSeconds.toLong(),
                  artworkUrl = navidromeRepository.getCoverArtUrl(server, song.coverArtId),
                  onClick = { onSongClick(song) },
                )
              }
            }
          }
        }

        NavidromeMusicTab.ALBUMS -> {
          if (uiState.isLoading && uiState.albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          } else {
            val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columns = if (isLandscape) 4 else 2
            LazyVerticalGrid(
              columns = GridCells.Fixed(columns),
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navigationBarHeight + 84.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(uiState.albums, key = { it.id }) { album ->
                SharedMusicGridCard(
                  title = album.title,
                  subtitle = album.artist,
                  artworkUrl = navidromeRepository.getCoverArtUrl(server, album.coverArtId),
                  onClick = { onAlbumClick(album) },
                )
              }
            }
          }
        }

        NavidromeMusicTab.ARTISTS -> {
          if (uiState.isLoading && uiState.artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          } else {
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 130.dp),
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navigationBarHeight + 84.dp),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              items(uiState.artists, key = { it.id }) { artist ->
                SharedMusicGridCard(
                  title = artist.name,
                  subtitle = null,
                  artworkUrl = navidromeRepository.getCoverArtUrl(server, artist.artistImageUrl),
                  isCircular = true,
                  cardWidth = 130.dp,
                  fallbackIcon = Icons.RoundedFilled.Person,
                  onClick = { onArtistClick(artist) },
                )
              }
            }
          }
        }

        NavidromeMusicTab.PLAYLISTS -> {
          if (uiState.isLoading && uiState.playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          } else {
            val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columns = if (isLandscape) 4 else 2
            LazyVerticalGrid(
              columns = GridCells.Fixed(columns),
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navigationBarHeight + 84.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(uiState.playlists, key = { it.id }) { playlist ->
                SharedMusicGridCard(
                  title = playlist.name,
                  subtitle = "${playlist.songCount} tracks",
                  artworkUrl = navidromeRepository.getCoverArtUrl(server, playlist.coverArtId),
                  onClick = { onPlaylistClick(playlist) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NavidromeHomeContent(
  uiState: NavidromeUiState,
  server: NavidromeServer,
  navidromeRepository: NavidromeRepository,
  onTabSelected: (NavidromeMusicTab) -> Unit,
  onSongClick: (NavidromeSong) -> Unit,
  onAlbumClick: (NavidromeAlbum) -> Unit,
  onArtistClick: (NavidromeArtist) -> Unit,
  onPlaylistClick: (NavidromePlaylist) -> Unit,
  navigationBarHeight: Dp,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = 16.dp, bottom = navigationBarHeight + 84.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // 1. Quick Mix / Jump back in
    if (uiState.jumpBackIn.isNotEmpty()) {
      item(key = "jump_back_in") {
        SharedCompactTrackGridSection(
          title = "Quick mix",
          tracks = uiState.jumpBackIn,
          getId = { it.id },
          getTitle = { it.title },
          getSubtitle = { it.artist },
          getArtworkUrl = { navidromeRepository.getCoverArtUrl(server, it.coverArtId, size = 200) },
          onTrackClick = onSongClick,
          onSeeAllClick = { onTabSelected(NavidromeMusicTab.TRACKS) },
        )
      }
    }

    // 2. Playlists row
    if (uiState.playlists.isNotEmpty()) {
      item(key = "playlists_row") {
        SharedMusicCarouselSection(
          title = "Playlists",
          items = uiState.playlists,
          getId = { it.id },
          getTitle = { it.name },
          getSubtitle = { "${it.songCount} tracks" },
          getArtworkUrl = { navidromeRepository.getCoverArtUrl(server, it.coverArtId) },
          fallbackIcon = Icons.RoundedFilled.QueueMusic,
          onClick = onPlaylistClick,
          onSeeAllClick = { onTabSelected(NavidromeMusicTab.PLAYLISTS) },
          cardWidth = 140.dp,
        )
      }
    }

    // 3. Recently added albums
    if (uiState.recentlyAddedAlbums.isNotEmpty()) {
      item(key = "recently_added_albums") {
        SharedMusicCarouselSection(
          title = "Recently added",
          items = uiState.recentlyAddedAlbums,
          getId = { it.id },
          getTitle = { it.title },
          getSubtitle = { it.artist },
          getArtworkUrl = { navidromeRepository.getCoverArtUrl(server, it.coverArtId) },
          onClick = onAlbumClick,
          onSeeAllClick = { onTabSelected(NavidromeMusicTab.ALBUMS) },
          cardWidth = 140.dp,
        )
      }
    }

    // 4. Artists to explore
    if (uiState.artistsToExplore.isNotEmpty()) {
      item(key = "artists_to_explore") {
        SharedMusicCarouselSection(
          title = "Artists to explore",
          items = uiState.artistsToExplore,
          getId = { it.id },
          getTitle = { it.name },
          getSubtitle = { "" },
          getArtworkUrl = { navidromeRepository.getCoverArtUrl(server, it.artistImageUrl) },
          isCircular = true,
          cardWidth = 130.dp,
          fallbackIcon = Icons.RoundedFilled.Person,
          onClick = onArtistClick,
          onSeeAllClick = { onTabSelected(NavidromeMusicTab.ARTISTS) },
        )
      }
    }
  }
}
