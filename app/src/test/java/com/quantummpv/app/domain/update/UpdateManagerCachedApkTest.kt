package com.quantummpv.app.domain.update

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class UpdateManagerCachedApkTest {
  private lateinit var context: Context
  private lateinit var cached: File
  private val name = "QuantumMPV-universal-v9.9.9.apk"
  private val payload = "verified apk bytes".toByteArray()

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    cached = File(File(context.externalCacheDir, "updates").apply { mkdirs() }, name)
    cached.writeBytes(payload)
  }

  private fun release(sha256: String?) =
    Release(
      tagName = "v9.9.9",
      name = "v9.9.9",
      body = "",
      publishedAt = "",
      assets = listOf(Asset("https://example.com/$name", name, payload.size.toLong(), "apk", sha256 = sha256)),
    )

  @Test
  fun returnsCachedApkOnlyWhenItsHashStillMatches() {
    val manager = UpdateManager(context)
    val hash = UpdateIntegrity.sha256Of(cached)

    assertEquals(cached, runBlocking { manager.getApkFile(release(hash)) })

    cached.writeBytes("tampered!!!!!!!!!!".toByteArray()) // same size, different bytes
    assertNull(runBlocking { manager.getApkFile(release(hash)) })
    assertFalse("a mismatching cache file is discarded", cached.exists())
  }

  @Test
  fun rejectsCachedApkWhenNoHashIsPublished() {
    assertNull(runBlocking { UpdateManager(context).getApkFile(release(sha256 = null)) })
    assertFalse("an unverifiable cache file is discarded", cached.exists())
  }
}
