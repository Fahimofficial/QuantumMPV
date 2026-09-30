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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SubtitleFontInstallerTest {
  @Test
  fun installsBundledFontIdempotentlyWithoutChangingUserFonts() {
    val context: Context = RuntimeEnvironment.getApplication()
    val fontsDirectory = File(context.filesDir, "fonts").apply { mkdirs() }
    val userFont = File(fontsDirectory, "MyCustomFont.ttf").apply { writeText("user font") }

    val installedFont = SubtitleFontInstaller.installStrict(context)
    assertTrue(installedFont.isFile)
    assertEquals(SubtitleFontInstaller.FONT_SIZE_BYTES, installedFont.length())
    assertEquals(SubtitleFontInstaller.FONT_SHA256, sha256(installedFont))
    assertEquals("user font", userFont.readText())

    val lastModified = installedFont.lastModified()
    val secondInstall = SubtitleFontInstaller.installStrict(context)
    assertEquals(installedFont.canonicalPath, secondInstall.canonicalPath)
    assertEquals(lastModified, secondInstall.lastModified())
  }

  private fun sha256(file: File): String =
    file.inputStream().use { input ->
      val digest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
      digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
