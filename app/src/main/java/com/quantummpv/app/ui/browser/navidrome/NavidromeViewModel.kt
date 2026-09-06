/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.navidrome

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quantummpv.app.data.navidrome.NavidromeClient
import com.quantummpv.app.data.navidrome.NavidromeSearchResult
import com.quantummpv.app.domain.navidrome.NavidromeAlbum
import com.quantummpv.app.domain.navidrome.NavidromeArtist
import com.quantummpv.app.domain.navidrome.NavidromeAuthMode
import com.quantummpv.app.domain.navidrome.NavidromeMusicTab
import com.quantummpv.app.domain.navidrome.NavidromePlaylist
import com.quantummpv.app.domain.navidrome.NavidromeServer
import com.quantummpv.app.domain.navidrome.NavidromeSong
import com.quantummpv.app.preferences.MediaServerPreferences
import com.quantummpv.app.preferences.MusicSourceProvider
import com.quantummpv.app.repository.NavidromeRepository
import com.quantummpv.app.utils.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class NavidromeUiState(
  val servers: List<NavidromeServer> = emptyList(),
  val activeServer: NavidromeServer? = null,
  val isLoading: Boolean = false,
  val error: String? = null,
  val activeTab: NavidromeMusicTab = NavidromeMusicTab.HOME,
  val jumpBackIn: List<NavidromeSong> = emptyList(),
  val playlists: List<NavidromePlaylist> = emptyList(),
  val recentlyAddedAlbums: List<NavidromeAlbum> = emptyList(),
  val artistsToExplore: List<NavidromeArtist> = emptyList(),
  val tracks: List<NavidromeSong> = emptyList(),
  val albums: List<NavidromeAlbum> = emptyList(),
  val artists: List<NavidromeArtist> = emptyList(),
  val searchQuery: String = "",
  val searchResult: NavidromeSearchResult? = null,
  val detailAlbum: NavidromeAlbum? = null,
  val detailArtist: NavidromeArtist? = null,
  val detailPlaylist: NavidromePlaylist? = null,
  val isConnectingServer: Boolean = false,
  val connectServerError: String? = null,
)

class NavidromeViewModel(
  application: Application,
) : AndroidViewModel(application), KoinComponent {
  private val navidromeRepository: NavidromeRepository by inject()
  private val navidromeClient: NavidromeClient by inject()
  private val mediaServerPreferences: MediaServerPreferences by inject()

  private val _uiState = MutableStateFlow(NavidromeUiState())
  val uiState: StateFlow<NavidromeUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      navidromeRepository.allServers.collect { servers ->
        _uiState.update { current ->
          val currentActive = current.activeServer
          val newActive = if (currentActive != null && servers.any { it.id == currentActive.id }) {
            servers.first { it.id == currentActive.id }
          } else {
            servers.firstOrNull()
          }
          current.copy(servers = servers, activeServer = newActive)
        }
        if (_uiState.value.activeServer != null) {
          loadAllData()
        }
      }
    }
  }

  fun setMusicTab(tab: NavidromeMusicTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun selectServer(server: NavidromeServer) {
    _uiState.update { it.copy(activeServer = server) }
    loadAllData()
  }

  fun deleteServer(server: NavidromeServer) {
    viewModelScope.launch(Dispatchers.IO) {
      navidromeRepository.deleteServer(server)
    }
  }

  fun onSearchQueryChanged(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    if (query.isBlank()) {
      _uiState.update { it.copy(searchResult = null) }
    } else {
      performSearch(query)
    }
  }

  private fun performSearch(query: String) {
    val server = _uiState.value.activeServer ?: return
    viewModelScope.launch(Dispatchers.IO) {
      val result = navidromeRepository.search(server, query).getOrNull()
      _uiState.update { it.copy(searchResult = result) }
    }
  }

  fun refresh() {
    loadAllData()
  }

  suspend fun refreshSuspend() {
    loadAllDataInternal()
  }

  private fun loadAllData() {
    viewModelScope.launch(Dispatchers.IO) {
      loadAllDataInternal()
    }
  }

  private suspend fun loadAllDataInternal() {
    val server = _uiState.value.activeServer ?: return
    _uiState.update { it.copy(isLoading = true, error = null) }

    try {
      // 1. Home quick mix & tracks
      val randomSongsResult = navidromeRepository.getRandomSongs(server, 50)
      val randomSongs = randomSongsResult.getOrDefault(emptyList())

      // 2. Playlists
      val playlistsResult = navidromeRepository.getPlaylists(server)
      val playlists = playlistsResult.getOrDefault(emptyList())

      // 3. Recently added albums
      val recentAlbumsResult = navidromeRepository.getAlbums(server, type = "recent", size = 20)
      val recentAlbums = recentAlbumsResult.getOrDefault(emptyList())

      // 4. Alphabetical albums
      val allAlbumsResult = navidromeRepository.getAlbums(server, type = "alphabeticalByName", size = 500)
      val allAlbums = allAlbumsResult.getOrDefault(emptyList())

      // 5. Artists
      val artistsResult = navidromeRepository.getArtists(server)
      val artists = artistsResult.getOrDefault(emptyList())

      _uiState.update {
        it.copy(
          isLoading = false,
          jumpBackIn = randomSongs.take(12),
          tracks = randomSongs,
          playlists = playlists,
          recentlyAddedAlbums = recentAlbums,
          artistsToExplore = artists.shuffled().take(15),
          albums = allAlbums,
          artists = artists,
        )
      }
    } catch (e: Exception) {
      _uiState.update { it.copy(isLoading = false, error = e.message) }
    }
  }

  fun openAlbumDetail(album: NavidromeAlbum) {
    val server = _uiState.value.activeServer ?: return
    _uiState.update { it.copy(detailAlbum = album, detailArtist = null, detailPlaylist = null) }
    viewModelScope.launch(Dispatchers.IO) {
      val fullAlbum = navidromeRepository.getAlbum(server, album.id).getOrNull()
      if (fullAlbum != null) {
        _uiState.update { it.copy(detailAlbum = fullAlbum) }
      }
    }
  }

  fun openArtistDetail(artist: NavidromeArtist) {
    val server = _uiState.value.activeServer ?: return
    _uiState.update { it.copy(detailArtist = artist, detailAlbum = null, detailPlaylist = null) }
    viewModelScope.launch(Dispatchers.IO) {
      val fullArtist = navidromeRepository.getArtist(server, artist.id).getOrNull()
      if (fullArtist != null) {
        _uiState.update { it.copy(detailArtist = fullArtist) }
      }
    }
  }

  fun openPlaylistDetail(playlist: NavidromePlaylist) {
    val server = _uiState.value.activeServer ?: return
    _uiState.update { it.copy(detailPlaylist = playlist, detailAlbum = null, detailArtist = null) }
    viewModelScope.launch(Dispatchers.IO) {
      val fullPlaylist = navidromeRepository.getPlaylist(server, playlist.id).getOrNull()
      if (fullPlaylist != null) {
        _uiState.update { it.copy(detailPlaylist = fullPlaylist) }
      }
    }
  }

  fun closeDetail() {
    _uiState.update { it.copy(detailAlbum = null, detailArtist = null, detailPlaylist = null) }
  }

  fun playSong(context: Context, song: NavidromeSong) {
    val server = _uiState.value.activeServer ?: return
    val contextTracks = _uiState.value.detailAlbum?.songs
      ?: _uiState.value.detailPlaylist?.songs
      ?: _uiState.value.tracks
    val trackList = if (contextTracks.any { it.id == song.id }) contextTracks else listOf(song)
    val startIndex = trackList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    playTrackList(context, server, trackList, startIndex)
  }

  fun playAll(context: Context, songs: List<NavidromeSong>, startIndex: Int = 0) {
    val server = _uiState.value.activeServer ?: return
    if (songs.isEmpty()) return
    playTrackList(context, server, songs, startIndex)
  }

  fun shufflePlay(context: Context, songs: List<NavidromeSong>) {
    val server = _uiState.value.activeServer ?: return
    if (songs.isEmpty()) return
    playTrackList(context, server, songs.shuffled(), 0)
  }

  private fun playTrackList(
    context: Context,
    server: NavidromeServer,
    songs: List<NavidromeSong>,
    startIndex: Int,
  ) {
    if (songs.isEmpty()) return
    val currentSong = songs.getOrNull(startIndex) ?: songs.first()
    val streamUrl = navidromeRepository.getStreamUrl(server, currentSong.id)
    val posterUrl = navidromeRepository.getCoverArtUrl(server, currentSong.coverArtId)

    val playlistUris = songs.map { Uri.parse(navidromeRepository.getStreamUrl(server, it.id)) }
    val playlistTitles = songs.map { it.title }
    val playlistArtists = songs.map { it.artist }
    val playlistArtworkUrls = songs.map { navidromeRepository.getCoverArtUrl(server, it.coverArtId) ?: "" }

    MediaUtils.playFile(
      source = streamUrl,
      context = context,
      launchSource = "navidrome_music",
      title = currentSong.title,
      posterUrl = posterUrl,
      playlist = playlistUris,
      playlistIndex = startIndex,
      playlistTitles = playlistTitles,
      playlistArtists = playlistArtists,
      playlistArtworkUrls = playlistArtworkUrls,
      isAudio = true,
    )
  }

  fun toggleFavorite(song: NavidromeSong) {
    val server = _uiState.value.activeServer ?: return
    val newFav = !song.isFavorite
    viewModelScope.launch(Dispatchers.IO) {
      navidromeRepository.toggleFavorite(server, song, newFav)
      _uiState.update { current ->
        current.copy(
          tracks = current.tracks.map { if (it.id == song.id) it.copy(isFavorite = newFav) else it },
          jumpBackIn = current.jumpBackIn.map { if (it.id == song.id) it.copy(isFavorite = newFav) else it },
          detailAlbum = current.detailAlbum?.copy(
            songs = current.detailAlbum.songs.map { if (it.id == song.id) it.copy(isFavorite = newFav) else it }
          ),
          detailPlaylist = current.detailPlaylist?.copy(
            songs = current.detailPlaylist.songs.map { if (it.id == song.id) it.copy(isFavorite = newFav) else it }
          ),
        )
      }
    }
  }

  fun toggleAlbumFavorite(album: NavidromeAlbum) {
    val server = _uiState.value.activeServer ?: return
    val newFav = !album.isFavorite
    viewModelScope.launch(Dispatchers.IO) {
      navidromeRepository.toggleAlbumFavorite(server, album, newFav)
      _uiState.update { current ->
        current.copy(
          albums = current.albums.map { if (it.id == album.id) it.copy(isFavorite = newFav) else it },
          recentlyAddedAlbums = current.recentlyAddedAlbums.map { if (it.id == album.id) it.copy(isFavorite = newFav) else it },
          detailAlbum = if (current.detailAlbum?.id == album.id) current.detailAlbum.copy(isFavorite = newFav) else current.detailAlbum,
        )
      }
    }
  }

  fun toggleArtistFavorite(artist: NavidromeArtist) {
    val server = _uiState.value.activeServer ?: return
    val newFav = !artist.isFavorite
    viewModelScope.launch(Dispatchers.IO) {
      navidromeRepository.toggleArtistFavorite(server, artist, newFav)
      _uiState.update { current ->
        current.copy(
          artists = current.artists.map { if (it.id == artist.id) it.copy(isFavorite = newFav) else it },
          artistsToExplore = current.artistsToExplore.map { if (it.id == artist.id) it.copy(isFavorite = newFav) else it },
          detailArtist = if (current.detailArtist?.id == artist.id) current.detailArtist.copy(isFavorite = newFav) else current.detailArtist,
        )
      }
    }
  }

  fun connectServer(
    serverUrl: String,
    serverName: String,
    authMode: NavidromeAuthMode = NavidromeAuthMode.CREDENTIALS,
    username: String = "",
    password: String = "",
    token: String = "",
    existingServer: NavidromeServer? = null,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isConnectingServer = true, connectServerError = null) }
      val cleanUrl = serverUrl.trim().trimEnd('/')

      val effectiveUsername = if (authMode == NavidromeAuthMode.TOKEN && username.isBlank()) {
        val extracted = navidromeClient.extractUsername(token)
        extracted ?: ""
      } else {
        username.trim()
      }

      if (effectiveUsername.isBlank()) {
        _uiState.update {
          it.copy(
            isConnectingServer = false,
            connectServerError = "Username is required for Subsonic authentication",
          )
        }
        return@launch
      }

      val displayName = serverName.trim().ifBlank {
        runCatching { Uri.parse(cleanUrl).host.orEmpty() }.getOrDefault("").ifBlank { "Navidrome ($effectiveUsername)" }
      }

      val candidateServer = NavidromeServer(
        id = existingServer?.id ?: 0,
        name = displayName,
        serverUrl = cleanUrl,
        username = effectiveUsername,
        password = password,
        token = token,
        authMode = authMode,
        lastConnected = System.currentTimeMillis(),
      )

      val pingResult = navidromeRepository.ping(candidateServer)
      if (pingResult.isSuccess) {
        val savedId = if (existingServer != null) {
          navidromeRepository.updateServer(candidateServer)
          existingServer.id
        } else {
          navidromeRepository.saveServer(candidateServer)
        }
        val savedServer = candidateServer.copy(id = savedId)
        _uiState.update {
          it.copy(
            isConnectingServer = false,
            connectServerError = null,
            activeServer = savedServer,
          )
        }
        mediaServerPreferences.musicSourceProvider.set(MusicSourceProvider.NAVIDROME)
        withContext(Dispatchers.Main) {
          onSuccess()
        }
      } else {
        val err = pingResult.exceptionOrNull()?.message ?: "Failed to connect to Navidrome server"
        _uiState.update {
          it.copy(isConnectingServer = false, connectServerError = err)
        }
      }
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          NavidromeViewModel(application)
        }
      }
  }
}
