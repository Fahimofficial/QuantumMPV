/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.repository

import android.util.Log
import com.quantummpv.app.data.audiobookshelf.AudiobookshelfClient
import com.quantummpv.app.data.network.credentials.AndroidNetworkCredentialKey
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.data.network.credentials.NetworkCredentialStorageException
import com.quantummpv.app.database.dao.AudiobookshelfServerDao
import com.quantummpv.app.database.entities.AudiobookshelfServerEntity
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfBook
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfLibrary
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfServer
import com.quantummpv.app.domain.audiobookshelf.AudiobookshelfTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class AudiobookshelfRepository(
  private val dao: AudiobookshelfServerDao,
  private val client: AudiobookshelfClient,
  private val credentialCipher: NetworkCredentialCipher =
    NetworkCredentialCipher(AndroidNetworkCredentialKey::getOrCreate),
) {
  val allServers: Flow<List<AudiobookshelfServer>> =
    dao.getAllServers().map { list -> list.map { decryptAndMigrate(it) } }.flowOn(Dispatchers.IO)

  suspend fun getServerById(id: Long): AudiobookshelfServer? =
    dao.getServerById(id)?.let { decryptAndMigrate(it) }

  suspend fun saveServer(server: AudiobookshelfServer): Long =
    dao.insert(server.toStorageEntity())

  suspend fun updateServer(server: AudiobookshelfServer) =
    dao.update(server.toStorageEntity())

  suspend fun deleteServer(server: AudiobookshelfServer) =
    dao.delete(AudiobookshelfServerEntity.fromDomain(server))

  suspend fun deleteServerById(id: Long) =
    dao.deleteById(id)

  suspend fun login(
    serverUrl: String,
    username: String,
    password: String,
  ): Result<AudiobookshelfServer> =
    client.login(serverUrl, username, password)

  suspend fun verifyToken(
    serverUrl: String,
    token: String,
    name: String = "",
  ): Result<AudiobookshelfServer> =
    client.verifyToken(serverUrl, token, name)

  suspend fun getLibraries(server: AudiobookshelfServer): Result<List<AudiobookshelfLibrary>> =
    client.getLibraries(server)

  suspend fun getItems(
    server: AudiobookshelfServer,
    libraryId: String,
    page: Int = 0,
    limit: Int = 200,
    sort: String? = null,
    desc: Boolean = false,
    filter: String? = null,
  ): Result<List<AudiobookshelfBook>> =
    client.getItems(server, libraryId, page, limit, sort, desc, filter)

  suspend fun getItemDetails(
    server: AudiobookshelfServer,
    itemId: String,
  ): Result<AudiobookshelfBook> =
    client.getItemDetails(server, itemId)

  suspend fun syncProgress(
    server: AudiobookshelfServer,
    itemId: String,
    currentTimeSeconds: Double,
    durationSeconds: Double,
    isFinished: Boolean = false,
  ): Result<Unit> =
    client.syncProgress(server, itemId, currentTimeSeconds, durationSeconds, isFinished)

  suspend fun syncProgress(
    serverId: Long,
    itemId: String,
    currentTimeSeconds: Double,
    durationSeconds: Double,
    isFinished: Boolean = false,
  ): Result<Unit> {
    val server = getServerById(serverId) ?: return Result.failure(Exception("Server not found"))
    return client.syncProgress(server, itemId, currentTimeSeconds, durationSeconds, isFinished)
  }

  fun getCoverUrl(server: AudiobookshelfServer, itemId: String): String =
    client.getCoverUrl(server, itemId)

  fun getTrackStreamUrl(server: AudiobookshelfServer, track: AudiobookshelfTrack, bookId: String): String =
    client.getTrackStreamUrl(server, track, bookId)

  /** Tokens are stored encrypted; rows written by earlier builds are re-encrypted on first read. */
  private suspend fun decryptAndMigrate(entity: AudiobookshelfServerEntity): AudiobookshelfServer {
    val token = decryptCredential(entity.token)
    if (entity.token.isNotEmpty() && !credentialCipher.isEncrypted(entity.token)) {
      // Token-only compare-and-set, so a concurrent updateServer() is never overwritten.
      runCatching { dao.replaceToken(entity.id, expectedToken = entity.token, newToken = encryptForStorage(token)) }
        .onFailure { Log.w(TAG, "Could not encrypt legacy Audiobookshelf token; keeping it for now", it) }
    }
    return entity.toDomain().copy(token = token)
  }

  /**
   * Returns an empty token when the Keystore key is gone (e.g. after a backup restore), so the
   * server stays listed and can be signed into again or removed instead of breaking the list.
   */
  private fun decryptCredential(storedValue: String): String {
    if (storedValue.isEmpty() || !credentialCipher.isEncrypted(storedValue)) return storedValue
    return try {
      credentialCipher.decrypt(storedValue)
    } catch (e: Exception) {
      Log.w(TAG, "Audiobookshelf token can no longer be decrypted; treating server as signed out", e)
      ""
    }
  }

  private fun AudiobookshelfServer.toStorageEntity(): AudiobookshelfServerEntity {
    val entity = AudiobookshelfServerEntity.fromDomain(this)
    return entity.copy(token = encryptForStorage(entity.token))
  }

  private fun encryptForStorage(value: String): String =
    if (value.isEmpty()) {
      ""
    } else {
      try {
        if (credentialCipher.isEncrypted(value)) value else credentialCipher.encrypt(value)
      } catch (e: Exception) {
        throw NetworkCredentialStorageException(e)
      }
    }

  private companion object {
    const val TAG = "AudiobookshelfRepository"
  }
}
