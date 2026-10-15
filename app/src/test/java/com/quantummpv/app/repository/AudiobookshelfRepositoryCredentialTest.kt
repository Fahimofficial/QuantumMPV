package com.quantummpv.app.repository

import com.quantummpv.app.data.audiobookshelf.AudiobookshelfClient
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.database.dao.AudiobookshelfServerDao
import com.quantummpv.app.database.entities.AudiobookshelfServerEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AudiobookshelfRepositoryCredentialTest {
  private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  @Test
  fun tokenFromAnotherDeviceKeepsServerListedAsSignedOut() {
    val oldDeviceKey = newKey()
    val restoredToken = NetworkCredentialCipher { oldDeviceKey }.encrypt("secret-token")
    val entity =
      AudiobookshelfServerEntity(
        id = 1,
        name = "Home",
        serverUrl = "https://abs.example",
        username = "me",
        token = restoredToken,
        userId = "u",
        activeLibraryId = null,
        lastConnected = 0,
      )
    val dao = mockk<AudiobookshelfServerDao>(relaxed = true)
    every { dao.getAllServers() } returns flowOf(listOf(entity))
    val currentKey = newKey()
    val repository = AudiobookshelfRepository(dao, mockk<AudiobookshelfClient>(), NetworkCredentialCipher { currentKey })

    val servers = runBlocking { repository.allServers.first() }

    assertEquals(1, servers.size)
    assertEquals("", servers.single().token)
    coVerify(exactly = 0) { dao.update(any()) }
  }
}
