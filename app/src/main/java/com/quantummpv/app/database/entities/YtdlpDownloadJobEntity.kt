package com.quantummpv.app.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ytdlp_download_jobs")
data class YtdlpDownloadJobEntity(
  @PrimaryKey val id: Int,
  val url: String,
  val title: String,
  val directory: String,
  val state: String = "QUEUED",
  val progressPercent: Float = 0f,
  val detail: String = "",
  val error: String? = null,
  val outputFile: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = createdAt,
)
