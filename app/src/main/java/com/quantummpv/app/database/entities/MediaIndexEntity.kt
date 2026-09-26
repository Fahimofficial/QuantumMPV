package com.quantummpv.app.database.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
  tableName = "media_index",
  indices = [
    Index(value = ["parentFolder"]),
    Index(value = ["mediaType"])
  ]
)
data class MediaIndexEntity(
  @PrimaryKey val uri: String,
  val path: String,
  val parentFolder: String,
  val displayName: String,
  val extension: String,
  val mediaType: Int, // 0 = Video, 1 = Audio
  val size: Long,
  val lastModified: Long,
  val durationMs: Long?,
  val hasThumbnail: Boolean,
)

@Fts4(contentEntity = MediaIndexEntity::class)
@Entity(tableName = "media_index_fts")
data class MediaIndexFtsEntity(
  @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int,
  val displayName: String,
  val path: String,
)
