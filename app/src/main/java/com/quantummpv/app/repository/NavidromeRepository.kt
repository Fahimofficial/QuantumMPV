/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository

import com.quantummpv.app.data.navidrome.NavidromeClient
import com.quantummpv.app.data.navidrome.NavidromeSearchResult
import com.quantummpv.app.data.network.credentials.AndroidNetworkCredentialKey
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.data.network.credentials.NetworkCredentialStorageException
import com.quantummpv.app.data.network.credentials.NetworkCredentialUnavailableException
import com.quantummpv.app.database.dao.NavidromeServerDao
import com.quantummpv.app.database.entities.NavidromeServerEntity
import com.quantummpv.app.domain.navidrome.NavidromeAlbum
import com.quantummpv.app.domain.navidrome.NavidromeArtist
import com.quantummpv.app.domain.navidrome.NavidromePlaylist
import com.quantummpv.app.domain.navidrome.NavidromeServer
import com.quantummpv.app.domain.navidrome.NavidromeSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class NavidromeRepository(
  private val dao: NavidromeServerDao,
  private val client: NavidromeClient,
  private val credentialCipher: NetworkCredentialCipher = NetworkCredentialCipher(AndroidNetworkCredentialKey::getOrCreate),
) {
  val allServers: Flow<List<NavidromeServer>> =
    dao.getAllServers().map { list -> list.map { decryptAndMigrate(it) } }.flowOn(Dispatchers.IO)

  val favoriteUpdates = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)

  suspend fun getServerById(id: Long): NavidromeServer? = dao.getServerById(id)?.let { decryptAndMigrate(it) }

  suspend fun saveServer(server: NavidromeServer): Long = dao.insert(server.toStorageEntity())

  suspend fun updateServer(server: NavidromeServer) = dao.update(server.toStorageEntity())

  suspend fun deleteServer(server: NavidromeServer) = dao.deleteById(server.id)

  suspend fun deleteServerById(id: Long) = dao.deleteById(id)

  suspend fun ping(server: NavidromeServer): Result<Boolean> = client.ping(server)
  suspend fun getArtists(server: NavidromeServer): Result<List<NavidromeArtist>> = client.getArtists(server)
  suspend fun getArtist(server: NavidromeServer, artistId: String): Result<NavidromeArtist> = client.getArtist(server, artistId)
  suspend fun getAlbums(server: NavidromeServer, type: String = "alphabeticalByName", size: Int = 500, offset: Int = 0): Result<List<NavidromeAlbum>> = client.getAlbums(server, type, size, offset)
  suspend fun getAlbum(server: NavidromeServer, albumId: String): Result<NavidromeAlbum> = client.getAlbum(server, albumId)
  suspend fun getRandomSongs(server: NavidromeServer, size: Int = 50): Result<List<NavidromeSong>> = client.getRandomSongs(server, size)
  suspend fun getPlaylists(server: NavidromeServer): Result<List<NavidromePlaylist>> = client.getPlaylists(server)
  suspend fun getPlaylist(server: NavidromeServer, playlistId: String): Result<NavidromePlaylist> = client.getPlaylist(server, playlistId)
  suspend fun search(server: NavidromeServer, query: String): Result<NavidromeSearchResult> = client.search(server, query)
  suspend fun getSong(server: NavidromeServer, songId: String): Result<NavidromeSong> = client.getSong(server, songId)
  suspend fun getStarred(server: NavidromeServer): Result<List<NavidromeSong>> = client.getStarred(server)

  suspend fun toggleFavorite(server: NavidromeServer, song: NavidromeSong, isFavorite: Boolean): Result<Unit> = toggleFavorite(server, song.id, isFavorite)

  suspend fun toggleFavorite(server: NavidromeServer, songId: String, isFavorite: Boolean): Result<Unit> {
    val res = if (isFavorite) client.starItem(server, id = songId) else client.unstarItem(server, id = songId)
    if (res.isSuccess) favoriteUpdates.tryEmit(songId to isFavorite)
    return res
  }

  suspend fun toggleAlbumFavorite(server: NavidromeServer, album: NavidromeAlbum, isFavorite: Boolean): Result<Unit> =
    if (isFavorite) client.starItem(server, albumId = album.id) else client.unstarItem(server, albumId = album.id)

  suspend fun toggleArtistFavorite(server: NavidromeServer, artist: NavidromeArtist, isFavorite: Boolean): Result<Unit> =
    if (isFavorite) client.starItem(server, artistId = artist.id) else client.unstarItem(server, artistId = artist.id)

  fun getStreamUrl(server: NavidromeServer, songId: String): String = client.getStreamUrl(server, songId)
  fun getCoverArtUrl(server: NavidromeServer, coverArtId: String?, size: Int = 500): String? = client.getCoverArtUrl(server, coverArtId, size)
  fun getArtistImageUrl(server: NavidromeServer, artist: NavidromeArtist, size: Int = 500): String? = client.getArtistImageUrl(server, artist, size)
  fun getSongCoverArtUrl(server: NavidromeServer, song: NavidromeSong, size: Int = 500): String? = client.getSongCoverArtUrl(server, song, size)

  private suspend fun decryptAndMigrate(entity: NavidromeServerEntity): NavidromeServer {
    val password = decryptCredential(entity.password)
    val token = decryptCredential(entity.token)
    val passwordWasPlaintext = entity.password.isNotEmpty() && !credentialCipher.isEncrypted(entity.password)
    val tokenWasPlaintext = entity.token.isNotEmpty() && !credentialCipher.isEncrypted(entity.token)
    if (passwordWasPlaintext || tokenWasPlaintext) {
      dao.update(entity.copy(password = encryptForStorage(password), token = encryptForStorage(token)))
    }
    return entity.toDomain().copy(password = password, token = token)
  }

  private fun decryptCredential(storedValue: String): String {
    if (storedValue.isEmpty() || !credentialCipher.isEncrypted(storedValue)) return storedValue
    return try { credentialCipher.decrypt(storedValue) } catch (e: Exception) { throw NetworkCredentialUnavailableException(e) }
  }

  private fun NavidromeServer.toStorageEntity(): NavidromeServerEntity {
    val entity = NavidromeServerEntity.fromDomain(this)
    return entity.copy(password = encryptForStorage(entity.password), token = encryptForStorage(entity.token))
  }

  private fun encryptForStorage(value: String): String =
    if (value.isEmpty()) "" else try {
      if (credentialCipher.isEncrypted(value)) value else credentialCipher.encrypt(value)
    } catch (e: Exception) {
      throw NetworkCredentialStorageException(e)
    }
}
