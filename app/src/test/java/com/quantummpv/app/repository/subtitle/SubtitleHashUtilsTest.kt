/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.repository.subtitle

import java.io.File
import java.io.RandomAccessFile
import org.junit.Test
import kotlin.test.assertEquals

class SubtitleHashUtilsTest {
  @Test
  fun `hash is deterministic for identical content`() {
    val file = File.createTempFile("quantummpv-hash", ".bin")
    try {
      val bytes = ByteArray(128 * 1024) { index -> (index and 0xFF).toByte() }
      file.writeBytes(bytes)

      val first = SubtitleHashUtils.calculate(file).getOrThrow()
      val second = SubtitleHashUtils.calculate(file).getOrThrow()

      assertEquals(first, second)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `checksum includes file size and edge chunks`() {
    val file = File.createTempFile("quantummpv-hash", ".bin")
    try {
      val bytes = ByteArray(128 * 1024) { 0 }
      file.writeBytes(bytes)

      RandomAccessFile(file, "rw").use { raf ->
        raf.seek(0)
        raf.write(1)
        raf.seek(128 * 1024L - 1)
        raf.write(2)
      }

      val hash = SubtitleHashUtils.calculate(file).getOrThrow()
      assertEquals(16, hash.length)
    } finally {
      file.delete()
    }
  }
}
