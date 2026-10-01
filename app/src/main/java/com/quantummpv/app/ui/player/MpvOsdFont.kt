/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import android.content.Context
import android.util.Log
import com.quantummpv.app.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** Installs the bundled app typeface where libmpv/fontconfig can use it for OSD text. */
internal object MpvOsdFont {
  const val FAMILY = "Google Sans Flex"
  const val SYSTEM_FAMILY = "sans-serif"
  internal const val FONT_FILE_NAME = "quantummpv-google-sans-flex.ttf"
  internal const val FONT_SIZE_BYTES = 3_997_148L
  internal const val FONT_SHA256 = "2510a8b7a24beb1fe8163e9a49813ccfe96b5453444b9443d42665ca4fa320c9"

  private const val TAG = "MpvOsdFont"

  /** Installs the verified bundled face before mpv/fontconfig performs its first font scan. */
  @Synchronized
  fun ensureInstalled(context: Context): File? {
    val appContext = context.applicationContext
    return try {
      installFromSource(
        directory = File(appContext.filesDir, "fonts"),
        fileName = FONT_FILE_NAME,
        expectedSizeBytes = FONT_SIZE_BYTES,
        expectedSha256 = FONT_SHA256,
      ) { appContext.resources.openRawResource(R.font.gflex_variable) }
    } catch (error: Exception) {
      Log.w(TAG, "Could not install the bundled OSD font; using the selected/system fallback.", error)
      null
    }
  }

  /** Source-injected copy routine allows cache-integrity regression tests without large fixtures. */
  @Synchronized
  internal fun installFromSource(
    directory: File,
    fileName: String,
    expectedSizeBytes: Long,
    expectedSha256: String,
    openSource: () -> InputStream,
  ): File {
    check(directory.isDirectory || directory.mkdirs()) {
      "Unable to create mpv fonts directory: $directory"
    }

    val installedFont = File(directory, fileName)
    if (
      installedFont.isFile &&
      installedFont.length() == expectedSizeBytes &&
      sha256(installedFont) == expectedSha256
    ) {
      return installedFont
    }

    val stagedFont = File(directory, ".$fileName.tmp")
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      openSource().use { source ->
        DigestInputStream(source, digest).use { input ->
          FileOutputStream(stagedFont).use { output -> input.copyTo(output) }
        }
      }
      check(stagedFont.length() == expectedSizeBytes) { "Bundled OSD font has an unexpected size." }
      check(digest.digest().toHex() == expectedSha256) { "Bundled OSD font checksum mismatch." }
      if (installedFont.exists()) {
        check(installedFont.delete()) { "Unable to replace stale OSD font: $installedFont" }
      }
      check(stagedFont.renameTo(installedFont)) { "Unable to install OSD font: $installedFont" }
      installedFont
    } catch (error: Exception) {
      stagedFont.delete()
      throw error
    }
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val bytesRead = input.read(buffer)
        if (bytesRead < 0) break
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().toHex()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
