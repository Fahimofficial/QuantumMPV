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
class SubtitleFontInstallerTest {
  @Test
  fun installsFontFromSourceIdempotentlyWithoutChangingUserFonts() {
    val context: Context = RuntimeEnvironment.getApplication()
    val fontsDirectory = File(context.filesDir, "fonts").apply { mkdirs() }
    val userFont = File(fontsDirectory, "MyCustomFont.ttf").apply { writeText("user font") }
    val fontBytes = "small valid font fixture".toByteArray()
    val expectedHash = sha256(fontBytes)
    val fileName = "QuantumMPV-idempotent-test-font.ttf"

    val installedFont = installFixture(fontsDirectory, fileName, fontBytes, expectedHash)
    assertTrue(installedFont.isFile)
    assertEquals(fontBytes.size.toLong(), installedFont.length())
    assertEquals(expectedHash, sha256(installedFont.readBytes()))
    assertEquals("user font", userFont.readText())

    val lastModified = installedFont.lastModified()
    val secondInstall = installFixture(fontsDirectory, fileName, fontBytes, expectedHash)
    assertEquals(installedFont.canonicalPath, secondInstall.canonicalPath)
    assertEquals(lastModified, secondInstall.lastModified())
  }

  @Test
  fun installsFallbackInMpvConfConfiguredSubFontsDirWithoutChangingUserFonts() {
    val context: Context = RuntimeEnvironment.getApplication()
    val configDirectory = File(context.cacheDir, "custom-mpv-config").apply { mkdirs() }
    val configuredPath = "~~/custom subtitle fonts"
    val resolvedFontsDirectory =
      SubtitleFontInstaller.resolveConfiguredDirectory(configDirectory, configuredPath)
        ?: error("Expected mpv.conf sub-fonts-dir to resolve")
    val existingUserFont = File(resolvedFontsDirectory, "MyCustomFont.ttf").apply {
      parentFile?.mkdirs()
      writeText("user font")
    }
    val mpvConf = File(configDirectory, "mpv.conf").apply {
      writeText("sub-fonts-dir=\"$configuredPath\"\n")
    }
    val originalConfig = mpvConf.readText()
    val fontBytes = "fallback for configured mpv font directory".toByteArray()
    val expectedHash = sha256(fontBytes)

    val installedFont =
      SubtitleFontInstaller.installInConfiguredDirectory(
        context = context,
        configDirectoryPath = configDirectory.path,
        configuredDirectory = configuredPath,
        fileName = "QuantumMPV-configured-test-font.ttf",
        expectedSizeBytes = fontBytes.size.toLong(),
        expectedSha256 = expectedHash,
        openSource = { ByteArrayInputStream(fontBytes) },
      ) ?: error("Expected fallback font to be installed in the configured directory")

    assertEquals(resolvedFontsDirectory.canonicalPath, installedFont.parentFile?.canonicalPath)
    assertArrayEquals(fontBytes, installedFont.readBytes())
    assertEquals("user font", existingUserFont.readText())
    assertEquals(originalConfig, mpvConf.readText())
  }

  @Test
  fun replacesSameLengthCorruptedInstalledFont() {
    val context: Context = RuntimeEnvironment.getApplication()
    val fontsDirectory = File(context.cacheDir, "corrupted-font-cache").apply { mkdirs() }
    val fontBytes = "expected bundled font fixture".toByteArray()
    val corruptedBytes = fontBytes.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
    val expectedHash = sha256(fontBytes)
    val fileName = "QuantumMPV-corrupted-test-font.ttf"
    val installedFont = File(fontsDirectory, fileName).apply { writeBytes(corruptedBytes) }
    assertEquals(fontBytes.size.toLong(), installedFont.length())
    assertTrue(sha256(installedFont.readBytes()) != expectedHash)

    val repairedFont = installFixture(fontsDirectory, fileName, fontBytes, expectedHash)

    assertArrayEquals(fontBytes, repairedFont.readBytes())
    assertEquals(expectedHash, sha256(repairedFont.readBytes()))
  }

  private fun installFixture(
    directory: File,
    fileName: String,
    bytes: ByteArray,
    hash: String,
  ): File =
    SubtitleFontInstaller.installFromSource(
      directory = directory,
      fileName = fileName,
      expectedSizeBytes = bytes.size.toLong(),
      expectedSha256 = hash,
      openSource = { ByteArrayInputStream(bytes) },
    )

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
      "%02x".format(it.toInt() and 0xff)
    }
}
