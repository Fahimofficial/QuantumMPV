/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quantummpv.app.database.entities.AudiobookshelfServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookshelfServerDao {
  @Query("SELECT * FROM audiobookshelf_servers ORDER BY lastConnected DESC")
  fun getAllServers(): Flow<List<AudiobookshelfServerEntity>>

  @Query("SELECT * FROM audiobookshelf_servers WHERE id = :id LIMIT 1")
  suspend fun getServerById(id: Long): AudiobookshelfServerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(server: AudiobookshelfServerEntity): Long

  @Update
  suspend fun update(server: AudiobookshelfServerEntity)

  @Delete
  suspend fun delete(server: AudiobookshelfServerEntity)

  /** Replaces only the token, and only if it still holds [expectedToken]; returns rows changed. */
  @Query("UPDATE audiobookshelf_servers SET token = :newToken WHERE id = :id AND token = :expectedToken")
  suspend fun replaceToken(
    id: Long,
    expectedToken: String,
    newToken: String,
  ): Int

  @Query("DELETE FROM audiobookshelf_servers WHERE id = :id")
  suspend fun deleteById(id: Long)
}
