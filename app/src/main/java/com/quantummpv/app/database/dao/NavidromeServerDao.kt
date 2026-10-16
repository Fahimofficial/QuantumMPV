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
import com.quantummpv.app.database.entities.NavidromeServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NavidromeServerDao {
  @Query("SELECT * FROM navidrome_servers ORDER BY lastConnected DESC")
  fun getAllServers(): Flow<List<NavidromeServerEntity>>

  @Query("SELECT * FROM navidrome_servers WHERE id = :id LIMIT 1")
  suspend fun getServerById(id: Long): NavidromeServerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(server: NavidromeServerEntity): Long

  @Update
  suspend fun update(server: NavidromeServerEntity)

  @Delete
  suspend fun delete(server: NavidromeServerEntity)

  @Query("DELETE FROM navidrome_servers WHERE id = :id")
  suspend fun deleteById(id: Long)

  /** Replaces only the credentials, and only if they are unchanged since they were read. */
  @Query(
    "UPDATE navidrome_servers SET password = :newPassword, token = :newToken " +
      "WHERE id = :id AND password = :expectedPassword AND token = :expectedToken",
  )
  suspend fun replaceCredentials(
    id: Long,
    expectedPassword: String,
    expectedToken: String,
    newPassword: String,
    newToken: String,
  ): Int
}
