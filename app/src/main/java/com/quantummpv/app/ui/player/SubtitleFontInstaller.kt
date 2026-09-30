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
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** Installs an app-owned Unicode fallback font where libmpv/fontconfig searches for fonts. */
internal object SubtitleFontInstaller {
  internal const val ASSET_PATH = "subtitle-fonts/GoNotoCurrent-Regular.ttf"
  internal const val FONT_FILE_NAME = "QuantumMPV-GoNotoCurrent-Regular-2.012.ttf"
  internal const val FONT_SIZE_BYTES = 14_700_060L
  internal const val FONT_SHA256 = "882afbab965608c2d2bc627fd8016b962aa5a6be2d358f9de24a7b5967c5632e"

  private const val TAG = "SubtitleFontInstaller"

  /**
   * Copies the bundled font once, before libmpv initializes. The unique, versioned filename keeps
   * the asset separate from user-installed fonts and lets a future font update use a new filename.
   * A failed copy is non-fatal: mpv can still use Android/fontconfig-provided system fonts.
   */
  @Synchronized
  fun install(context: Context): File? {
    return try {
      installStrict(context)
    } catch (error: Exception) {
      Log.w(TAG, "Could not install the bundled subtitle fallback font; using system fonts.", error)
      null
    }
  }

  @Synchronized
  internal fun installStrict(context: Context): File {
    val appContext = context.applicationContext
    val fontsDirectory = File(appContext.filesDir, "fonts")
    val installedFont = File(fontsDirectory, FONT_FILE_NAME)
    if (installedFont.isFile && installedFont.length() == FONT_SIZE_BYTES) {
      return installedFont
    }

    val stagedFont = File(fontsDirectory, ".$FONT_FILE_NAME.tmp")
    return try {
      check(fontsDirectory.isDirectory || fontsDirectory.mkdirs()) {
        "Unable to create subtitle fonts directory: $fontsDirectory"
      }

      val digest = MessageDigest.getInstance("SHA-256")
      appContext.assets.open(ASSET_PATH).use { asset ->
        DigestInputStream(asset, digest).use { input ->
          FileOutputStream(stagedFont).use { output -> input.copyTo(output) }
        }
      }

      check(stagedFont.length() == FONT_SIZE_BYTES) { "Bundled subtitle font has an unexpected size." }
      val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
      check(actualSha256 == FONT_SHA256) { "Bundled subtitle font checksum mismatch." }
      if (installedFont.exists()) {
        check(installedFont.delete()) { "Unable to replace stale subtitle font: $installedFont" }
      }
      check(stagedFont.renameTo(installedFont)) { "Unable to install subtitle font: $installedFont" }
      installedFont
    } catch (error: Exception) {
      stagedFont.delete()
      throw error
    }
  }
}
