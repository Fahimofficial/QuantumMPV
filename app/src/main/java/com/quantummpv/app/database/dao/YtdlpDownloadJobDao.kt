package com.quantummpv.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quantummpv.app.database.entities.YtdlpDownloadJobEntity

@Dao
interface YtdlpDownloadJobDao {
  @Query("SELECT * FROM ytdlp_download_jobs ORDER BY createdAt ASC")
  suspend fun getAll(): List<YtdlpDownloadJobEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(job: YtdlpDownloadJobEntity)

  @Update
  suspend fun update(job: YtdlpDownloadJobEntity)

  @Query("DELETE FROM ytdlp_download_jobs WHERE id = :id")
  suspend fun delete(id: Int)

  @Query("UPDATE ytdlp_download_jobs SET state = 'RUNNING', updatedAt = :updatedAt WHERE id = :id AND state = 'QUEUED'")
  suspend fun claimQueued(id: Int, updatedAt: Long = System.currentTimeMillis()): Int

  @Query("UPDATE ytdlp_download_jobs SET state = 'QUEUED', updatedAt = :updatedAt WHERE state = 'RUNNING'")
  suspend fun requeueInterrupted(updatedAt: Long = System.currentTimeMillis()): Int
}
