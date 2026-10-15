package com.quantummpv.app.database.repository

import com.quantummpv.app.database.dao.MediaIndexDao
import com.quantummpv.app.database.entities.MediaIndexEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MediaIndexRepository(private val dao: MediaIndexDao) {
  fun searchMedia(query: String): Flow<List<MediaIndexEntity>> {
    val ftsQuery = com.quantummpv.app.database.dao.MediaIndexFtsQuery.from(query) ?: return flowOf(emptyList())
    return dao.searchMedia(ftsQuery)
  }
}
