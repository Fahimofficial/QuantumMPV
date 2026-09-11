/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.repository.subtitle

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * OpenSubtitles-style movie hash helper.
 *
 * The hash is deliberately calculated from the file size and 64 KiB from the beginning and end
 * of the media. It is an additional subtitle matching signal, never a replacement for title,
 * season/episode, language, or release matching.
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

    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(CHUNK_SIZE)

    input.seek(0L)
    input.readFully(buffer)
    digest.update(buffer)

    input.seek(fileSize - CHUNK_SIZE)
    input.readFully(buffer)
    digest.update(buffer)

    // The canonical OpenSubtitles hash also folds the file size into a 64-bit checksum. Keep the
    // algorithm explicit here rather than confusing it with a plain MD5 digest.
    var checksum = fileSize
    val longBuffer = ByteArray(8)
    input.seek(0L)
    repeat(CHUNK_SIZE / 8) {
      input.readFully(longBuffer)
      checksum += littleEndianLong(longBuffer)
    }
    input.seek(fileSize - CHUNK_SIZE)
    repeat(CHUNK_SIZE / 8) {
      input.readFully(longBuffer)
      checksum += littleEndianLong(longBuffer)
    }

    return checksum.toString(16).padStart(16, '0')
  }

  private fun littleEndianLong(bytes: ByteArray): Long {
    var value = 0L
    for (index in 0 until 8) {
      value = value or ((bytes[index].toLong() and 0xFFL) shl (index * 8))
    }
    return value
  }
}
