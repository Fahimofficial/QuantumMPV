package com.quantummpv.app.presentation.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RemoteImageDownloadTest {
  @Test
  fun copiesBodiesWithinTheLimit() {
    val body = ByteArray(10_000) { it.toByte() }
    val output = ByteArrayOutputStream()

    assertTrue(RemoteImageLoader.copyBounded(ByteArrayInputStream(body), output, limit = 10_000))
    assertArrayEquals(body, output.toByteArray())
  }

  @Test
  fun rejectsBodiesLargerThanTheLimit() {
    val output = ByteArrayOutputStream()

    assertFalse(RemoteImageLoader.copyBounded(ByteArrayInputStream(ByteArray(10_001)), output, limit = 10_000))
  }

  @Test
  fun oversizedResponseLeavesNoCacheOrTemporaryFile() {
    val directory = Files.createTempDirectory("remote-image-oversized").toFile()
    val cacheFile = File(directory, "image.png")

    assertFalse(RemoteImageLoader.writeBoundedAtomically(ByteArrayInputStream(ByteArray(101)), cacheFile, limit = 100))
    assertFalse(cacheFile.exists())
    assertTrue(directory.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun interruptedResponseLeavesNoCacheOrTemporaryFile() {
    val directory = Files.createTempDirectory("remote-image-interrupted").toFile()
    val cacheFile = File(directory, "image.png")
    val input =
      object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
        override fun read(
          buffer: ByteArray,
          offset: Int,
          length: Int,
        ): Int {
          if (pos > 0) throw IOException("connection interrupted")
          return super.read(buffer, offset, length.coerceAtMost(1))
        }
      }

    assertFalse(RemoteImageLoader.writeBoundedAtomically(input, cacheFile, limit = 100))
    assertFalse(cacheFile.exists())
    assertTrue(directory.listFiles().orEmpty().isEmpty())
  }
}
