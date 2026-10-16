package com.quantummpv.app.repository

import com.quantummpv.app.data.jellyfin.JellyfinClient
import com.quantummpv.app.data.navidrome.NavidromeClient
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.database.dao.JellyfinServerDao
import com.quantummpv.app.database.dao.NavidromeServerDao
import com.quantummpv.app.database.entities.JellyfinServerEntity
import com.quantummpv.app.database.entities.NavidromeServerEntity
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

/**
 * JellyfinViewModel and NavidromeViewModel collect allServers without a catch, so a credential
 * that can no longer be decrypted (backup restored on another device) used to crash the app the
 * moment the Jellyfin tab or Media servers screen opened. It must read as "signed out" instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaServerCredentialRecoveryTest {
  private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  private val oldDeviceCipher = newKey().let { key -> NetworkCredentialCipher { key } }
  private val currentKey = newKey()
  private val currentCipher = NetworkCredentialCipher { currentKey }

  @Test
  fun jellyfinServerWithUnreadableTokenStaysListedAsSignedOut() {
    val entity =
      JellyfinServerEntity(1, "Home", "https://jf.example", "u", "me", oldDeviceCipher.encrypt("secret"))
    val dao = mockk<JellyfinServerDao>(relaxed = true)
    every { dao.getAllServers() } returns flowOf(listOf(entity))

    val servers = runBlocking { JellyfinRepository(dao, mockk<JellyfinClient>(), currentCipher).allServers.first() }

    assertEquals(1, servers.size)
    assertEquals("", servers.single().accessToken)
  }

  @Test
  fun jellyfinLegacyTokenMigratesWithTokenOnlyCompareAndSet() {
    val entity = JellyfinServerEntity(2, "Home", "https://jf.example", "u", "me", "plain")
    val dao = mockk<JellyfinServerDao>(relaxed = true)
    every { dao.getAllServers() } returns flowOf(listOf(entity))

    val servers = runBlocking { JellyfinRepository(dao, mockk<JellyfinClient>(), currentCipher).allServers.first() }

    assertEquals("plain", servers.single().accessToken)
    coVerify(exactly = 1) { dao.replaceAccessToken(2, "plain", match { currentCipher.isEncrypted(it) }) }
    coVerify(exactly = 0) { dao.update(any()) }
  }

  @Test
  fun navidromeServerWithUnreadableCredentialsStaysListedAsSignedOut() {
    val entity =
      NavidromeServerEntity(
        id = 3,
        name = "Music",
        serverUrl = "https://nd.example",
        username = "me",
        password = oldDeviceCipher.encrypt("pw"),
        token = oldDeviceCipher.encrypt("tok"),
      )
    val dao = mockk<NavidromeServerDao>(relaxed = true)
    every { dao.getAllServers() } returns flowOf(listOf(entity))

    val servers = runBlocking { NavidromeRepository(dao, mockk<NavidromeClient>(), currentCipher).allServers.first() }

    assertEquals(1, servers.size)
    assertEquals("", servers.single().password)
    assertEquals("", servers.single().token)
  }
}
