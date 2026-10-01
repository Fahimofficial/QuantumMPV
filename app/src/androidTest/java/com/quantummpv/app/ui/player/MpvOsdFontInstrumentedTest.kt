/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class MpvOsdFontInstrumentedTest {
  @Test
  fun installsAndReusesBundledGoogleSansFlexFromAndroidResources() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val installed = MpvOsdFont.ensureInstalled(context) ?: error("Expected bundled OSD font to install")

    assertTrue(installed.isFile)
    assertEquals(MpvOsdFont.FONT_SIZE_BYTES, installed.length())
    assertEquals(MpvOsdFont.FONT_SHA256, sha256(installed.readBytes()))

    val lastModified = installed.lastModified()
    val secondInstall = MpvOsdFont.ensureInstalled(context) ?: error("Expected cached OSD font to remain available")
    assertEquals(installed.canonicalPath, secondInstall.canonicalPath)
    assertEquals(lastModified, secondInstall.lastModified())
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
      "%02x".format(it.toInt() and 0xff)
    }
}
