/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database.repository

import android.content.Context
import android.net.Uri
import com.quantummpv.app.database.dao.PlaylistDao
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.database.entities.PlaylistItemEntity
import com.quantummpv.app.utils.media.M3UParseResult
import com.quantummpv.app.utils.media.M3UParser
import com.quantummpv.app.utils.media.M3UPlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

class PlaylistRepository(
  private val playlistDao: PlaylistDao,
  private val httpClient: OkHttpClient,
) {
  companion object {
    const val FAVORITES_PLAYLIST_NAME = "Favorites"
  }

  // Playlist operations
  suspend fun createPlaylist(
    name: String,
    isAudio: Boolean = false,
  ): Long {
    val now = System.currentTimeMillis()
    return playlistDao.insertPlaylist(
      PlaylistEntity(
        name = name,
        createdAt = now,
        updatedAt = now,
        isAudio = isAudio,
      ),
    )
  }

  suspend fun getOrCreateFavoritesPlaylist(isAudio: Boolean = true): PlaylistEntity {
    val existing = playlistDao.getAllPlaylists().find {
      it.name.equals(FAVORITES_PLAYLIST_NAME, ignoreCase = true) && it.isAudio == isAudio
    }
    if (existing != null) return existing

    val id = createPlaylist(name = FAVORITES_PLAYLIST_NAME, isAudio = isAudio)
    return getPlaylistById(id.toInt()) ?: PlaylistEntity(
      id = id.toInt(),
      name = FAVORITES_PLAYLIST_NAME,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      isAudio = isAudio,
    )
  }

  fun isProtectedPlaylist(playlist: PlaylistEntity): Boolean {
    return playlist.name.equals(FAVORITES_PLAYLIST_NAME, ignoreCase = true)
  }

  fun observeIsFavorite(filePath: String, isAudio: Boolean = true): Flow<Boolean> =
    playlistDao.observeAllPlaylists().map { playlists ->
      if (filePath.isBlank()) return@map false
      val favPlaylist = playlists.find { it.name.equals(FAVORITES_PLAYLIST_NAME, ignoreCase = true) && it.isAudio == isAudio }
        ?: return@map false
      val items = playlistDao.getPlaylistItems(favPlaylist.id)
      items.any { isPathMatching(it.filePath, filePath) }
    }

  suspend fun isFavorite(filePath: String, isAudio: Boolean = true): Boolean {
    if (filePath.isBlank()) return false
    val favPlaylist = playlistDao.getAllPlaylists().find {
      it.name.equals(FAVORITES_PLAYLIST_NAME, ignoreCase = true) && it.isAudio == isAudio
    } ?: return false
    val items = playlistDao.getPlaylistItems(favPlaylist.id)
    return items.any { isPathMatching(it.filePath, filePath) }
  }

  suspend fun toggleFavorite(filePath: String, fileName: String, isAudio: Boolean = true): Boolean {
    if (filePath.isBlank()) return false
    val cleanPath = when {
      filePath.startsWith("file://") -> Uri.parse(filePath).path ?: filePath
      else -> filePath
    }
    val favPlaylist = getOrCreateFavoritesPlaylist(isAudio)
    val items = playlistDao.getPlaylistItems(favPlaylist.id)
    val existing = items.find { isPathMatching(it.filePath, cleanPath) }
    return if (existing != null) {
      removeItemFromPlaylist(existing)
      false
    } else {
      addItemToPlaylist(favPlaylist.id, cleanPath, fileName)
      true
    }
  }

  private fun isPathMatching(pathA: String, pathB: String): Boolean {
    if (pathA == pathB) return true
    if (pathA.isBlank() || pathB.isBlank()) return false
    val cleanA = if (pathA.startsWith("file://")) Uri.parse(pathA).path ?: pathA else pathA
    val cleanB = if (pathB.startsWith("file://")) Uri.parse(pathB).path ?: pathB else pathB
    if (cleanA == cleanB) return true
    val uriA = runCatching { Uri.parse(pathA) }.getOrNull()
    val uriB = runCatching { Uri.parse(pathB) }.getOrNull()
    if (uriA != null && uriB != null && uriA == uriB) return true
    if (uriA?.path != null && uriB?.path != null && uriA.path == uriB.path) return true
    if (uriA?.path != null && uriA.path == cleanB) return true
    if (uriB?.path != null && uriB.path == cleanA) return true
    return false
  }

  suspend fun updatePlaylist(playlist: PlaylistEntity) {
    playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    if (isProtectedPlaylist(playlist)) return
    playlistDao.deletePlaylist(playlist)
  }

  fun prioritizeFavorites(playlists: List<PlaylistEntity>): List<PlaylistEntity> {
    return playlists.sortedWith(
      compareByDescending<PlaylistEntity> { isProtectedPlaylist(it) }
        .thenBy { it.name.lowercase() }
    )
  }

  fun observeAllPlaylists(isAudio: Boolean? = null): Flow<List<PlaylistEntity>> =
    playlistDao.observeAllPlaylists().map { playlists ->
      val filtered = if (isAudio == null) {
        playlists
      } else {
        classifyAndFilterPlaylists(playlists, isAudio)
      }
      prioritizeFavorites(filtered)
    }

  suspend fun getAllPlaylists(isAudio: Boolean? = null): List<PlaylistEntity> {
    val playlists = playlistDao.getAllPlaylists()
    val filtered = if (isAudio == null) {
      playlists
    } else {
      classifyAndFilterPlaylists(playlists, isAudio)
    }
    return prioritizeFavorites(filtered)
  }

  private suspend fun classifyAndFilterPlaylists(
    playlists: List<PlaylistEntity>,
    targetIsAudio: Boolean,
  ): List<PlaylistEntity> {
    val result = mutableListOf<PlaylistEntity>()
    for (playlist in playlists) {
      // M3U/IPTV lists can mix radio and video entries. Keep them in the playlist section
      // where they were imported instead of moving the whole list after spotting one audio URL.
      if (playlist.isM3uPlaylist) {
        if (!targetIsAudio) result.add(playlist)
        continue
      }
      var effectiveIsAudio = playlist.isAudio
      if (!effectiveIsAudio) {
        val items = playlistDao.getPlaylistItems(playlist.id)
        if (items.isNotEmpty()) {
          val hasAudioItems = items.any { com.quantummpv.app.utils.storage.FileTypeUtils.isAudioFile(java.io.File(it.filePath)) }
          if (hasAudioItems) {
            effectiveIsAudio = true
            playlistDao.updatePlaylist(playlist.copy(isAudio = true))
          }
        }
      }
      if (effectiveIsAudio == targetIsAudio) {
        result.add(playlist)
      }
    }
    return prioritizeFavorites(result)
  }

  suspend fun getPlaylistById(playlistId: Int): PlaylistEntity? = playlistDao.getPlaylistById(playlistId)

  fun observePlaylistById(playlistId: Int): Flow<PlaylistEntity?> = playlistDao.observePlaylistById(playlistId)

  // Playlist item operations
  suspend fun addItemToPlaylist(
    playlistId: Int,
    filePath: String,
    fileName: String,
  ) {
    val maxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
    playlistDao.insertPlaylistItem(
      PlaylistItemEntity(
        playlistId = playlistId,
        filePath = filePath,
        fileName = fileName,
        position = maxPosition + 1,
        addedAt = System.currentTimeMillis(),
      ),
    )
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun addItemsToPlaylist(
    playlistId: Int,
    items: List<Pair<String, String>>,
  ) {
    val maxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
    val now = System.currentTimeMillis()
    val playlistItems =
      items.mapIndexed { index, (filePath, fileName) ->
        PlaylistItemEntity(
          playlistId = playlistId,
          filePath = filePath,
          fileName = fileName,
          position = maxPosition + 1 + index,
          addedAt = now,
        )
      }
    playlistDao.insertPlaylistItemsAtomically(playlistItems)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemFromPlaylist(item: PlaylistItemEntity) {
    playlistDao.deletePlaylistItem(item)
    getPlaylistById(item.playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemsFromPlaylist(items: List<PlaylistItemEntity>) {
    if (items.isEmpty()) return
    playlistDao.deletePlaylistItems(items)
    getPlaylistById(items.first().playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemById(itemId: Int) {
    playlistDao.deletePlaylistItemById(itemId)
  }

  suspend fun clearPlaylist(playlistId: Int) {
    playlistDao.deleteAllItemsFromPlaylist(playlistId)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  fun observePlaylistItems(playlistId: Int): Flow<List<PlaylistItemEntity>> =
    playlistDao.observePlaylistItems(playlistId)

  suspend fun getPlaylistItems(playlistId: Int): List<PlaylistItemEntity> = playlistDao.getPlaylistItems(playlistId)

  fun observePlaylistItemCount(playlistId: Int): Flow<Int> = playlistDao.observePlaylistItemCount(playlistId)

  suspend fun getPlaylistItemCount(playlistId: Int): Int = playlistDao.getPlaylistItemCount(playlistId)

  suspend fun reorderPlaylistItems(
    playlistId: Int,
    newOrder: List<Int>,
  ) {
    playlistDao.reorderPlaylistItems(playlistId, newOrder)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun getPlaylistItemsAsUris(playlistId: Int): List<Uri> =
    getPlaylistItems(playlistId).map {
      Uri.parse(it.filePath)
    }

  /**
   * Get a windowed subset of playlist items as URIs to avoid loading huge playlists at once.
   */
  suspend fun getPlaylistItemsWindowAsUris(
    playlistId: Int,
    centerIndex: Int = 0,
    windowSize: Int = 100,
  ): List<Uri> {
    val totalCount = getPlaylistItemCount(playlistId)
    if (totalCount == 0) return emptyList()

    if (totalCount <= windowSize) {
      return getPlaylistItemsAsUris(playlistId)
    }

    val halfWindow = windowSize / 2
    val startPosition = (centerIndex - halfWindow).coerceAtLeast(0)
    val endPosition = (startPosition + windowSize).coerceAtMost(totalCount)

    return playlistDao
      .getPlaylistItemsInRange(playlistId, startPosition, endPosition)
      .map { Uri.parse(it.filePath) }
  }

  // Play history operations
  suspend fun updatePlayHistory(
    playlistId: Int,
    filePath: String,
    position: Long = 0,
  ) {
    playlistDao.updatePlayHistory(playlistId, filePath, System.currentTimeMillis(), position)
  }

  suspend fun getRecentlyPlayedInPlaylist(
    playlistId: Int,
    limit: Int = 20,
  ): List<PlaylistItemEntity> = playlistDao.getRecentlyPlayedInPlaylist(playlistId, limit)

  fun observeRecentlyPlayedInPlaylist(
    playlistId: Int,
    limit: Int = 20,
  ): Flow<List<PlaylistItemEntity>> = playlistDao.observeRecentlyPlayedInPlaylist(playlistId, limit)

  suspend fun getPlaylistItemByPath(
    playlistId: Int,
    filePath: String,
  ): PlaylistItemEntity? = playlistDao.getPlaylistItemByPath(playlistId, filePath)

  // Category / Favorites
  fun observeDistinctCategories(playlistId: Int): Flow<List<String>> = playlistDao.observeDistinctCategories(playlistId)

  suspend fun getDistinctCategories(playlistId: Int): List<String> = playlistDao.getDistinctCategories(playlistId)

  fun observeFavoriteItems(playlistId: Int): Flow<List<PlaylistItemEntity>> =
    playlistDao.observeFavoriteItems(playlistId)

  suspend fun toggleFavorite(itemId: Int) = playlistDao.toggleFavorite(itemId)

  suspend fun setFavorite(
    itemId: Int,
    isFavorite: Boolean,
  ) = playlistDao.setFavorite(itemId, isFavorite)

  // M3U Playlist operations
  suspend fun createM3UPlaylist(
    url: String,
    userAgent: String? = null,
  ): Result<Long> =
    try {
      val parseResult = M3UParser.parseFromUrl(url, userAgent, httpClient = httpClient)

      when (parseResult) {
        is M3UParseResult.Success -> {
          val playlistId =
            persistM3UPlaylist(
              parseResult = parseResult,
              name = parseResult.playlistName,
              sourceUrl = M3UParser.sanitizeSourceUrl(url),
              userAgent = userAgent,
            )
          Result.success(playlistId)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun createM3UPlaylistFromFile(
    context: Context,
    uri: Uri,
  ): Result<Long> =
    try {
      val parseResult = M3UParser.parseFromUri(context, uri)

      when (parseResult) {
        is M3UParseResult.Success -> {
          val playlistId =
            persistM3UPlaylist(
              parseResult = parseResult,
              name = parseResult.playlistName,
              sourceUrl = null,
            )
          Result.success(playlistId)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun createM3UPlaylistFromContent(
    content: String,
    sourceName: String,
    sourceUrl: String? = null,
    userAgent: String? = null,
  ): Result<Long> =
    try {
      val parseResult = M3UParser.parseContent(content, sourceUrl ?: sourceName)

      when (parseResult) {
        is M3UParseResult.Success -> {
          val playlistId =
            persistM3UPlaylist(
              parseResult = parseResult,
              name = parseResult.playlistName.ifBlank { sourceName.substringBeforeLast('.') },
              sourceUrl = sourceUrl?.let(M3UParser::sanitizeSourceUrl),
              userAgent = userAgent,
            )
          Result.success(playlistId)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /** Persists an already bounded/parsed playlist without materializing and parsing its text again. */
  suspend fun createM3UPlaylistFromParsed(
    parseResult: M3UParseResult.Success,
    sourceName: String,
    sourceUrl: String? = null,
    userAgent: String? = null,
  ): Result<Long> =
    try {
      val playlistId =
        persistM3UPlaylist(
          parseResult = parseResult,
          name = parseResult.playlistName.ifBlank { sourceName.substringBeforeLast('.') },
          sourceUrl = sourceUrl?.let(M3UParser::sanitizeSourceUrl),
          userAgent = userAgent,
        )
      Result.success(playlistId)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Result.failure(error)
    }

  suspend fun refreshM3UPlaylist(playlistId: Int): Result<Unit> {
    return try {
      val playlist =
        getPlaylistById(playlistId)
          ?: return Result.failure(Exception("Playlist not found"))

      if (!playlist.isM3uPlaylist || playlist.m3uSourceUrl == null) {
        return Result.failure(Exception("Not an M3U playlist or no source URL available"))
      }

      val parseResult = M3UParser.parseFromUrl(playlist.m3uSourceUrl, playlist.userAgent, httpClient = httpClient)

      when (parseResult) {
        is M3UParseResult.Success -> {
          // Preserve favorite URLs before clearing
          val favoritePaths = playlistDao.getFavoriteFilePaths(playlistId).toSet()

          val now = System.currentTimeMillis()
          val items =
            parseResult.items.mapIndexed { index, m3uItem ->
              m3uItem.toEntity(
                playlistId = playlistId,
                position = index,
                now = now,
                // Restore favorite status for paths that were favorited before refresh
                isFavorite = m3uItem.url in favoritePaths,
              )
            }

          playlistDao.replacePlaylistItems(playlist.copy(updatedAt = now), items)

          Result.success(Unit)
        }
        is M3UParseResult.Error -> {
          Result.failure(Exception(parseResult.message, parseResult.exception))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private suspend fun persistM3UPlaylist(
    parseResult: M3UParseResult.Success,
    name: String,
    sourceUrl: String?,
    userAgent: String? = null,
  ): Long {
    val now = System.currentTimeMillis()
    val playlist =
      PlaylistEntity(
        name = name,
        createdAt = now,
        updatedAt = now,
        m3uSourceUrl = sourceUrl,
        isM3uPlaylist = true,
        userAgent = userAgent,
      )
    val items =
      parseResult.items.mapIndexed { index, item ->
        item.toEntity(playlistId = 0, position = index, now = now)
      }
    return playlistDao.insertPlaylistWithItems(playlist, items)
  }
}

private fun M3UPlaylistItem.toEntity(
  playlistId: Int,
  position: Int,
  now: Long,
  isFavorite: Boolean = false,
): PlaylistItemEntity =
  PlaylistItemEntity(
    playlistId = playlistId,
    filePath = M3UParser.normalizeLocalMediaReference(url),
    fileName = title ?: tvgName ?: url.substringAfterLast('/').take(80).ifBlank { "Item ${position + 1}" },
    position = position,
    addedAt = now,
    tvgId = tvgId,
    tvgLogo = tvgLogo,
    groupTitle = groupTitle,
    licenseType = licenseType,
    licenseKey = licenseKey,
    userAgent = userAgent,
    isFavorite = isFavorite,
  )
