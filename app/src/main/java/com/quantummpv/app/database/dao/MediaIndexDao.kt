package com.quantummpv.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quantummpv.app.database.entities.MediaIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaIndexDao {
  @Query("SELECT * FROM media_index WHERE parentFolder = :folderPath ORDER BY displayName ASC")
  fun getMediaInFolder(folderPath: String): Flow<List<MediaIndexEntity>>

  @Query("SELECT DISTINCT parentFolder FROM media_index")
  fun getAllFolders(): Flow<List<String>>

  @Query("""
    SELECT media_index.* FROM media_index 
    JOIN media_index_fts ON media_index.uri = media_index_fts.rowid 
    WHERE media_index_fts MATCH :query 
    ORDER BY media_index.displayName ASC
  """)
  fun searchMedia(query: String): Flow<List<MediaIndexEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(mediaList: List<MediaIndexEntity>)

  @Query("DELETE FROM media_index WHERE uri = :uri")
  suspend fun deleteByUri(uri: String)

  @Query("DELETE FROM media_index WHERE parentFolder = :folderPath")
  suspend fun deleteByFolder(folderPath: String)

  @Query("DELETE FROM media_index")
  suspend fun clearAll()
}
