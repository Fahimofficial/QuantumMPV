/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quantummpv.app.database.converters.NetworkProtocolConverter
import com.quantummpv.app.database.converters.NetworkStreamEntryTypeConverter
import com.quantummpv.app.database.dao.DirectoryScanDao
import com.quantummpv.app.database.dao.DownloadItemDao
import com.quantummpv.app.database.dao.NetworkConnectionDao
import com.quantummpv.app.database.dao.NetworkStreamEntryDao
import com.quantummpv.app.database.dao.PlaybackStateDao
import com.quantummpv.app.database.dao.PlaylistDao
import com.quantummpv.app.database.dao.RecentlyPlayedDao
import com.quantummpv.app.database.dao.SecureMediaDao
import com.quantummpv.app.database.dao.VideoMetadataDao
import com.quantummpv.app.database.dao.JellyfinServerDao
import com.quantummpv.app.database.dao.NavidromeServerDao
import com.quantummpv.app.database.entities.DirectoryScanEntity
import com.quantummpv.app.database.entities.DownloadItemEntity
import com.quantummpv.app.database.entities.JellyfinServerEntity
import com.quantummpv.app.database.entities.NavidromeServerEntity
import com.quantummpv.app.database.entities.NetworkStreamEntryEntity
import com.quantummpv.app.database.entities.PlaybackStateEntity
import com.quantummpv.app.database.entities.PlaylistEntity
import com.quantummpv.app.database.entities.PlaylistItemEntity
import com.quantummpv.app.database.entities.RecentlyPlayedEntity
import com.quantummpv.app.database.entities.SecureMediaEntity
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
    SecureMediaEntity::class,
    NetworkStreamEntryEntity::class,
    JellyfinServerEntity::class,
    DownloadItemEntity::class,
    NavidromeServerEntity::class,
  ],
  version = 21,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class, NetworkStreamEntryTypeConverter::class)
abstract class MpvRxDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun networkStreamEntryDao(): NetworkStreamEntryDao

  abstract fun playlistDao(): PlaylistDao

  abstract fun directoryScanDao(): DirectoryScanDao

  abstract fun secureMediaDao(): SecureMediaDao

  abstract fun jellyfinServerDao(): JellyfinServerDao

  abstract fun downloadItemDao(): DownloadItemDao

  abstract fun navidromeServerDao(): NavidromeServerDao
}
