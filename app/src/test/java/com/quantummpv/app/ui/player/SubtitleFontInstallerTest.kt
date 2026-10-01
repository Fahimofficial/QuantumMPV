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
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SubtitleFontInstallerTest {
  @Test
  fun installsFontFromSourceIdempotentlyWithoutChangingUserFonts() {
    val context: Context = RuntimeEnvironment.getApplication()
    val fontsDirectory = File(context.filesDir, "fonts").apply { mkdirs() }
    val userFont = File(fontsDirectory, "MyCustomFont.ttf").apply { writeText("user font") }
    val fontBytes = "small valid font fixture".toByteArray()
    val expectedHash = sha256(fontBytes)

    val installedFont = installFixture(context, fontBytes, expectedHash)
    assertTrue(installedFont.isFile)
    assertEquals(fontBytes.size.toLong(), installedFont.length())
    assertEquals(expectedHash, sha256(installedFont.readBytes()))
    assertEquals("user font", userFont.readText())

    val lastModified = installedFont.lastModified()
    val secondInstall = installFixture(context, fontBytes, expectedHash)
    assertEquals(installedFont.canonicalPath, secondInstall.canonicalPath)
    assertEquals(lastModified, secondInstall.lastModified())
  }

  private fun installFixture(context: Context, bytes: ByteArray, hash: String): File =
    SubtitleFontInstaller.installFromSource(
      context = context,
      fileName = "QuantumMPV-test-font.ttf",
      expectedSizeBytes = bytes.size.toLong(),
      expectedSha256 = hash,
      openSource = { ByteArrayInputStream(bytes) },
    )

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
      "%02x".format(it.toInt() and 0xff)
    }
}
