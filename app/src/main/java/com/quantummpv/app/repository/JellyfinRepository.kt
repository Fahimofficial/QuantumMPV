/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository

import com.quantummpv.app.data.jellyfin.JellyfinClient
import com.quantummpv.app.data.network.credentials.AndroidNetworkCredentialKey
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.data.network.credentials.NetworkCredentialStorageException
import com.quantummpv.app.data.network.credentials.NetworkCredentialUnavailableException
import com.quantummpv.app.database.dao.JellyfinServerDao
import com.quantummpv.app.database.entities.JellyfinServerEntity
import com.quantummpv.app.domain.jellyfin.JellyfinAuthResult
import com.quantummpv.app.domain.jellyfin.JellyfinItem
import com.quantummpv.app.domain.jellyfin.JellyfinServer
import com.quantummpv.app.domain.jellyfin.JellyfinUser
import com.quantummpv.app.utils.media.PlaybackSubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class JellyfinRepository(
  private val dao: JellyfinServerDao,
  private val client: JellyfinClient,
  private val credentialCipher: NetworkCredentialCipher = NetworkCredentialCipher(AndroidNetworkCredentialKey::getOrCreate),
) {
  val allServers: Flow<List<JellyfinServer>> =
    dao.getAllServers().map { list -> list.map { decryptAndMigrate(it) } }.flowOn(Dispatchers.IO)

  suspend fun getServerById(id: Long): JellyfinServer? = dao.getServerById(id)?.let { decryptAndMigrate(it) }

  suspend fun saveServer(server: JellyfinServer): Long = dao.insert(server.toStorageEntity())

  suspend fun updateServer(server: JellyfinServer) = dao.update(server.toStorageEntity())

  suspend fun deleteServer(server: JellyfinServer) = dao.deleteById(server.id)

  suspend fun authenticate(serverUrl: String, username: String, password: String): Result<JellyfinAuthResult> =
    client.authenticate(serverUrl, username, password)

  suspend fun validateToken(serverUrl: String, token: String): Result<JellyfinUser> =
    client.validateToken(serverUrl, token)

  suspend fun getLibraries(server: JellyfinServer): Result<List<JellyfinItem>> =
    client.getUserLibraries(server.serverUrl, server.userId, server.accessToken)

  suspend fun getResumeItems(server: JellyfinServer, limit: Int = 16): Result<List<JellyfinItem>> =
    client.getResumeItems(server.serverUrl, server.userId, server.accessToken, limit)

  suspend fun getLatestMedia(server: JellyfinServer, parentId: String? = null, limit: Int = 16, groupItems: Boolean = true): Result<List<JellyfinItem>> =
    client.getLatestMedia(server.serverUrl, server.userId, parentId, limit, server.accessToken, groupItems)

  suspend fun getSuggestions(server: JellyfinServer, limit: Int = 16): Result<List<JellyfinItem>> =
    client.getSuggestions(server.serverUrl, server.userId, limit, server.accessToken)

  suspend fun getSimilarItems(server: JellyfinServer, itemId: String, limit: Int = 12): Result<List<JellyfinItem>> =
    client.getSimilarItems(server.serverUrl, server.userId, itemId, limit, server.accessToken)

  suspend fun getItem(server: JellyfinServer, itemId: String): Result<JellyfinItem> =
    client.getItem(server.serverUrl, server.userId, itemId, server.accessToken)

  suspend fun deleteItem(server: JellyfinServer, itemId: String): Result<Unit> =
    client.deleteItem(server.serverUrl, itemId, server.accessToken)

  suspend fun getItems(
    server: JellyfinServer,
    parentId: String? = null,
    artistIds: String? = null,
    searchTerm: String? = null,
    sortBy: com.quantummpv.app.domain.jellyfin.JellyfinSortBy = com.quantummpv.app.domain.jellyfin.JellyfinSortBy.NAME,
    sortOrder: com.quantummpv.app.domain.jellyfin.JellyfinSortOrder = com.quantummpv.app.domain.jellyfin.JellyfinSortOrder.ASCENDING,
    isPlayed: Boolean? = null,
    isFavorite: Boolean? = null,
    genres: String? = null,
    includeItemTypes: String? = null,
    startIndex: Int = 0,
    limit: Int = 100,
  ): Result<com.quantummpv.app.domain.jellyfin.JellyfinQueryResult> = client.getItems(serverUrl = server.serverUrl, userId = server.userId, parentId = parentId, artistIds = artistIds, searchTerm = searchTerm, sortBy = sortBy, sortOrder = sortOrder, isPlayed = isPlayed, isFavorite = isFavorite, genres = genres, includeItemTypes = includeItemTypes, startIndex = startIndex, limit = limit, token = server.accessToken)

  suspend fun getArtists(
    server: JellyfinServer,
    parentId: String? = null,
    sortBy: com.quantummpv.app.domain.jellyfin.JellyfinSortBy = com.quantummpv.app.domain.jellyfin.JellyfinSortBy.NAME,
    sortOrder: com.quantummpv.app.domain.jellyfin.JellyfinSortOrder = com.quantummpv.app.domain.jellyfin.JellyfinSortOrder.ASCENDING,
    startIndex: Int = 0,
    limit: Int = 500,
    albumArtistsOnly: Boolean = false,
  ): Result<com.quantummpv.app.domain.jellyfin.JellyfinQueryResult> = client.getArtists(serverUrl = server.serverUrl, userId = server.userId, parentId = parentId, sortBy = sortBy, sortOrder = sortOrder, startIndex = startIndex, limit = limit, token = server.accessToken, albumArtistsOnly = albumArtistsOnly)

  suspend fun getGenres(server: JellyfinServer, parentId: String): Result<List<String>> = client.getGenres(server.serverUrl, server.userId, parentId, server.accessToken)
  suspend fun getSeasons(server: JellyfinServer, seriesId: String): Result<List<JellyfinItem>> = client.getSeasons(server.serverUrl, server.userId, seriesId, server.accessToken)
  suspend fun getEpisodes(server: JellyfinServer, seriesId: String, seasonId: String): Result<List<JellyfinItem>> = client.getEpisodes(server.serverUrl, server.userId, seriesId, seasonId, server.accessToken)
  suspend fun getSubtitleTracks(server: JellyfinServer, itemId: String): Result<List<PlaybackSubtitleTrack>> = client.getSubtitleTracks(server.serverUrl, server.accessToken, server.userId, itemId)

  fun getStreamUrl(server: JellyfinServer, item: JellyfinItem): String = client.getStreamUrl(serverUrl = server.serverUrl, itemId = item.id, token = server.accessToken, isAudio = item.isAudio)

  fun getImageUrl(server: JellyfinServer, item: JellyfinItem, maxWidth: Int = 400): String {
    val targetItemId = if (item.primaryImageTag.isNullOrBlank() && !item.albumId.isNullOrBlank() && !item.albumPrimaryImageTag.isNullOrBlank()) item.albumId else item.id
    val targetTag = item.primaryImageTag ?: item.albumPrimaryImageTag
    return client.getImageUrl(serverUrl = server.serverUrl, itemId = targetItemId, imageTag = targetTag, maxWidth = maxWidth, token = server.accessToken)
  }

  fun getBackdropUrl(server: JellyfinServer, item: JellyfinItem, maxWidth: Int = 1280): String = client.getBackdropUrl(serverUrl = server.serverUrl, itemId = item.id, imageTag = item.backdropImageTag, maxWidth = maxWidth, token = server.accessToken)

  suspend fun reportPlaybackStart(serverUrl: String, token: String, itemId: String, positionTicks: Long) = client.reportPlaybackStart(serverUrl, token, itemId, positionTicks)
  suspend fun reportPlaybackProgress(serverUrl: String, token: String, itemId: String, positionTicks: Long, isPaused: Boolean = false) = client.reportPlaybackProgress(serverUrl, token, itemId, positionTicks, isPaused)
  suspend fun reportPlaybackStopped(serverUrl: String, token: String, itemId: String, positionTicks: Long) = client.reportPlaybackStopped(serverUrl, token, itemId, positionTicks)
  suspend fun markPlayed(server: JellyfinServer, item: JellyfinItem): Result<Unit> = client.markPlayed(serverUrl = server.serverUrl, userId = server.userId, itemId = item.id, token = server.accessToken)
  suspend fun markUnplayed(server: JellyfinServer, item: JellyfinItem): Result<Unit> = client.markUnplayed(serverUrl = server.serverUrl, userId = server.userId, itemId = item.id, token = server.accessToken)
  suspend fun toggleFavorite(server: JellyfinServer, item: JellyfinItem, isFavorite: Boolean): Result<Unit> = client.toggleFavorite(serverUrl = server.serverUrl, userId = server.userId, itemId = item.id, isFavorite = isFavorite, token = server.accessToken)
  suspend fun toggleFavorite(server: JellyfinServer, itemId: String, isFavorite: Boolean): Result<Unit> = client.toggleFavorite(serverUrl = server.serverUrl, userId = server.userId, itemId = itemId, isFavorite = isFavorite, token = server.accessToken)
  suspend fun createPlaylist(server: JellyfinServer, name: String, itemIds: List<String> = emptyList()): Result<String> = client.createPlaylist(serverUrl = server.serverUrl, userId = server.userId, token = server.accessToken, name = name, itemIds = itemIds)
  suspend fun addToPlaylist(server: JellyfinServer, playlistId: String, itemIds: List<String>): Result<Unit> = client.addToPlaylist(serverUrl = server.serverUrl, userId = server.userId, token = server.accessToken, playlistId = playlistId, itemIds = itemIds)

  private suspend fun decryptAndMigrate(entity: JellyfinServerEntity): JellyfinServer {
    if (entity.accessToken.isEmpty()) return entity.toDomain()
    val token = if (credentialCipher.isEncrypted(entity.accessToken)) {
      try { credentialCipher.decrypt(entity.accessToken) } catch (e: Exception) { throw NetworkCredentialUnavailableException(e) }
    } else {
      val encrypted = encryptForStorage(entity.accessToken)
      dao.update(entity.copy(accessToken = encrypted))
      entity.accessToken
    }
    return entity.toDomain().copy(accessToken = token)
  }

  private fun JellyfinServer.toStorageEntity(): JellyfinServerEntity =
    JellyfinServerEntity.fromDomain(this).let { entity -> entity.copy(accessToken = encryptForStorage(entity.accessToken)) }

  private fun encryptForStorage(value: String): String =
    if (value.isEmpty()) "" else try {
      if (credentialCipher.isEncrypted(value)) value else credentialCipher.encrypt(value)
    } catch (e: Exception) {
      throw NetworkCredentialStorageException(e)
    }
}
