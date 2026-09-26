package com.quantummpv.app.database.repository

import com.quantummpv.app.database.dao.MediaIndexDao
import com.quantummpv.app.database.entities.MediaIndexEntity
import kotlinx.coroutines.flow.Flow

class MediaIndexRepository(private val dao: MediaIndexDao) {
  fun searchMedia(query: String): Flow<List<MediaIndexEntity>> {
    val ftsQuery = if (query.isBlank()) "*" else "*$query*"
    return dao.searchMedia(ftsQuery)
  }
}
