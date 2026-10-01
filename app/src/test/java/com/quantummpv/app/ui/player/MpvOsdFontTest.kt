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
import com.quantummpv.app.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class MpvOsdFontTest {
  @Test
  fun installsBundledGoogleSansFlexIdempotently() {
    val context: Context = RuntimeEnvironment.getApplication()
    val installed = MpvOsdFont.ensureInstalled(context) ?: error("Expected bundled OSD font to install")

    assertTrue(installed.isFile)
    assertEquals(MpvOsdFont.FONT_SIZE_BYTES, installed.length())
    assertEquals(MpvOsdFont.FONT_SHA256, sha256(installed.readBytes()))

    val lastModified = installed.lastModified()
    val secondInstall = MpvOsdFont.ensureInstalled(context) ?: error("Expected cached OSD font to remain available")
    assertEquals(installed.canonicalPath, secondInstall.canonicalPath)
    assertEquals(lastModified, secondInstall.lastModified())
  }

  @Test
  fun repairsSameLengthCorruptedOsdFontCache() {
    val context: Context = RuntimeEnvironment.getApplication()
    val directory = File(context.cacheDir, "corrupted-osd-font").apply { mkdirs() }
    val expectedBytes = "valid OSD font fixture".toByteArray()
    val corruptedBytes = expectedBytes.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
    val expectedHash = sha256(expectedBytes)
    val fileName = "quantummpv-corrupted-osd-test.ttf"
    val cached = File(directory, fileName).apply { writeBytes(corruptedBytes) }
    assertEquals(expectedBytes.size.toLong(), cached.length())
    assertTrue(sha256(cached.readBytes()) != expectedHash)

    val repaired =
      MpvOsdFont.installFromSource(
        directory = directory,
        fileName = fileName,
        expectedSizeBytes = expectedBytes.size.toLong(),
        expectedSha256 = expectedHash,
        openSource = { ByteArrayInputStream(expectedBytes) },
      )

    assertArrayEquals(expectedBytes, repaired.readBytes())
    assertEquals(expectedHash, sha256(repaired.readBytes()))
  }

  @Test
  fun mirrorsGoogleSansFlexIntoMpvConfConfiguredFontDirectory() {
    val context: Context = RuntimeEnvironment.getApplication()
    val configDirectory = File(context.cacheDir, "custom-osd-mpv-config").apply { mkdirs() }
    val configuredPath = "~~/custom-fonts"

    val mirroredFont =
      SubtitleFontInstaller.installInConfiguredDirectory(
        context = context,
        configDirectoryPath = configDirectory.path,
        configuredDirectory = configuredPath,
        fileName = MpvOsdFont.FONT_FILE_NAME,
        expectedSizeBytes = MpvOsdFont.FONT_SIZE_BYTES,
        expectedSha256 = MpvOsdFont.FONT_SHA256,
        openSource = { context.resources.openRawResource(R.font.gflex_variable) },
      ) ?: error("Expected Google Sans Flex to be mirrored into mpv.conf's font directory")

    assertEquals(MpvOsdFont.FONT_SIZE_BYTES, mirroredFont.length())
    assertEquals(MpvOsdFont.FONT_SHA256, sha256(mirroredFont.readBytes()))
    assertEquals("custom-fonts", mirroredFont.parentFile?.name)
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
      "%02x".format(it.toInt() and 0xff)
    }
}
