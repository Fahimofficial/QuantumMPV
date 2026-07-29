/*
 * SPDX-License-Identifier: CC-BY-NC-4.0
 *
 * This work is licensed under Creative Commons Attribution-NonCommercial 4.0 International License.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by-nc/4.0/
 */

package com.quantummpv.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quantummpv.app.database.converters.NetworkProtocolConverter
import com.quantummpv.app.database.dao.DirectoryScanDao
import com.quantummpv.app.database.dao.NetworkConnectionDao
import com.quantummpv.app.database.dao.PlaybackStateDao
import com.quantummpv.app.database.dao.PlaylistDao
import com.quantummpv.app.database.dao.RecentlyPlayedDao
import com.quantummpv.app.database.dao.VideoMetadataDao
import com.quantummpv.app.database.entities.DirectoryScanEntity
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.database.entities.PlaylistItemEntity
import com.quantummpv.app.database.entities.RecentlyPlayedEntity
import com.quantummpv.app.database.entities.VideoMetadataEntity
import com.quantummpv.app.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
    DirectoryScanEntity::class,
  ],
  version = 10,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class MpvRxDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao

  abstract fun directoryScanDao(): DirectoryScanDao
}
