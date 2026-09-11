/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.repository.subtitle

import java.io.File
import java.io.RandomAccessFile

/**
 * OpenSubtitles-compatible media hash helper.
 *
 * The checksum is formed from the 64 KiB at the start and end of the file plus the file size,
 * using unsigned 64-bit little-endian words. It is an additional subtitle-matching signal, not a
 * replacement for title, season/episode, language, or release matching.
 */
object SubtitleHashUtils {
  private const val CHUNK_SIZE = 64 * 1024

  fun calculate(file: File): Result<String> =
    runCatching {
      require(file.isFile) { "Media file does not exist: ${file.path}" }
      require(file.length() >= CHUNK_SIZE * 2L) {
        "Media file is too small for OpenSubtitles-style hashing"
      }

      RandomAccessFile(file, "r").use { input ->
        calculate(input, file.length())
      }
    }

  fun calculate(input: RandomAccessFile, fileSize: Long): String {
    require(fileSize >= CHUNK_SIZE * 2L) { "File must contain at least two 64 KiB chunks" }

    var checksum = fileSize
    checksum += checksumChunk(input, 0L)
    checksum += checksumChunk(input, fileSize - CHUNK_SIZE)
    return java.lang.Long.toUnsignedString(checksum, 16).padStart(16, '0')
  }

  private fun checksumChunk(
    input: RandomAccessFile,
    offset: Long,
  ): Long {
    val buffer = ByteArray(8)
    var checksum = 0L
    input.seek(offset)
    repeat(CHUNK_SIZE / 8) {
      input.readFully(buffer)
      var value = 0L
      for (index in 0 until 8) {
        value = value or ((buffer[index].toLong() and 0xFFL) shl (index * 8))
      }
      checksum += value
    }
    return checksum
  }
}
