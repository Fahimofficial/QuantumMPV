package com.quantummpv.app.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YtdlpDownloadEngineTest {
  private fun job(id: Int, title: String = "Video", url: String = "https://example.test/video"): YtdlpDownloadEngine.Job =
    YtdlpDownloadEngine.Job(
      id = id,
      url = url,
      title = title,
      directory = "/tmp/quantummpv-test",
    )

  @Test
  fun `sanitization removes unsafe path characters`() {
    val sanitized = DownloadLocations.sanitizeName("Funny / Video : ? * < > | \\ \"")
    assertFalse(sanitized.contains("/"))
    assertFalse(sanitized.contains("\\"))
    assertFalse(sanitized.contains(":"))
  }

  @Test
  fun `same title jobs have different persistent prefixes`() {
    val first = YtdlpDownloadEngine.jobFilePrefix(job(42))
    val second = YtdlpDownloadEngine.jobFilePrefix(job(43))

    assertEquals("Video-42-", first)
    assertNotEquals(first, second)
    assertTrue(YtdlpDownloadEngine.outputTemplate(job(42)).contains("Video-42-%(extractor)s-%(id)s"))
  }

  @Test
  fun `same url jobs remain isolated by persistent id`() {
    val first = job(10, url = "https://example.test/same")
    val second = job(11, url = "https://example.test/same")

    assertNotEquals(YtdlpDownloadEngine.jobFilePrefix(first), YtdlpDownloadEngine.jobFilePrefix(second))
    assertTrue(YtdlpDownloadEngine.isJobFile(first, "Video-10-youtube-abc.mp4"))
    assertFalse(YtdlpDownloadEngine.isJobFile(first, "Video-11-youtube-abc.mp4"))
  }

  @Test
  fun `same title with different urls remains isolated`() {
    val first = job(20, url = "https://example.test/one")
    val second = job(21, url = "https://example.test/two")

    assertNotEquals(YtdlpDownloadEngine.outputTemplate(first), YtdlpDownloadEngine.outputTemplate(second))
  }

  @Test
  fun `legacy part never changes a new job to title-only output`() {
    val directory = createTempDir(prefix = "legacy-part-")
    try {
      File(directory, "Video.mp4.part").createNewFile()
      val current = job(30, title = "Video").copy(directory = directory.absolutePath)

      assertEquals(
        "${directory.absolutePath}/Video-30-%(extractor)s-%(id)s.%(ext)s",
        YtdlpDownloadEngine.outputTemplate(current),
      )
      assertFalse(YtdlpDownloadEngine.isJobFile(current, "Video.mp4.part"))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `legacy ytdl never changes a new job to title-only output`() {
    val directory = createTempDir(prefix = "legacy-ytdl-")
    try {
      File(directory, "Video.mp4.ytdl").createNewFile()
      val current = job(31, title = "Video").copy(directory = directory.absolutePath)

      assertTrue(YtdlpDownloadEngine.outputTemplate(current).contains("Video-31-"))
      assertFalse(YtdlpDownloadEngine.isJobFile(current, "Video.mp4.ytdl"))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `output discovery cannot return another jobs output`() {
    val directory = createTempDir(prefix = "discovery-")
    try {
      val current = job(40, title = "Shared").copy(directory = directory.absolutePath)
      val other = File(directory, "Shared-41-youtube-other.mp4").apply { createNewFile() }
      val own = File(directory, "Shared-40-youtube-own.mp4").apply {
        createNewFile()
        setLastModified(System.currentTimeMillis() + 1_000)
      }
      File(directory, "Shared.mp4.part").createNewFile()

      assertEquals(own, YtdlpDownloadEngine.findNewestOutput(current, directory.listFiles()!!.toList()))
      assertFalse(YtdlpDownloadEngine.isJobFile(current, other.name))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `cleanup candidates contain only the current job prefix`() {
    val directory = createTempDir(prefix = "cleanup-")
    try {
      val current = job(50, title = "Shared").copy(directory = directory.absolutePath)
      val ownPart = File(directory, "Shared-50-youtube-own.mp4.part").apply { createNewFile() }
      val ownYtdl = File(directory, "Shared-50-youtube-own.mp4.ytdl").apply { createNewFile() }
      val other = File(directory, "Shared-51-youtube-other.mp4").apply { createNewFile() }
      val legacy = File(directory, "Shared.mp4.part").apply { createNewFile() }

      val cleanup = YtdlpDownloadEngine.filesToCleanup(current, directory.listFiles()!!.toList())
      assertTrue(cleanup.contains(ownPart))
      assertTrue(cleanup.contains(ownYtdl))
      assertFalse(cleanup.contains(other))
      assertFalse(cleanup.contains(legacy))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `retry and restored jobs preserve output identity`() {
    val original = job(60, title = "Retry me")
    val retry = original.copy(state = YtdlpDownloadEngine.JobState.QUEUED)
    val restored = original.copy(state = YtdlpDownloadEngine.JobState.RUNNING)

    assertEquals(YtdlpDownloadEngine.jobFilePrefix(original), YtdlpDownloadEngine.jobFilePrefix(retry))
    assertEquals(YtdlpDownloadEngine.outputTemplate(original), YtdlpDownloadEngine.outputTemplate(restored))
  }

  @Test
  fun `unknown extractor and media ids still remain job scoped`() {
    val current = job(70, title = "Video")

    assertTrue(YtdlpDownloadEngine.isJobFile(current, "Video-70---.mp4"))
    assertFalse(YtdlpDownloadEngine.isJobFile(current, "Video-7-youtube-0.mp4"))
    assertFalse(YtdlpDownloadEngine.isJobFile(current, "Video-700-youtube-0.mp4"))
  }

  @Test
  fun `empty and long unsafe titles produce stable scoped prefixes`() {
    val empty = job(80, title = "///")
    val long = job(81, title = "a".repeat(500))

    assertEquals("download-80-", YtdlpDownloadEngine.jobFilePrefix(empty))
    assertTrue(YtdlpDownloadEngine.jobFilePrefix(long).endsWith("-81-"))
  }
}
