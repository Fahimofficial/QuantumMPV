/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.quantummpv.app.R
import java.io.File
import java.io.InputStream

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
      SubtitleFontInstaller.installFromSource(
        directory = File(appContext.filesDir, "fonts"),
        fileName = FONT_FILE_NAME,
        expectedSizeBytes = FONT_SIZE_BYTES,
        expectedSha256 = FONT_SHA256,
      ) { openFontResource(appContext) }
    } catch (error: Exception) {
      Log.w(TAG, "Could not install the bundled OSD font; using the selected/system fallback.", error)
      null
    }
  }

  @SuppressLint("ResourceType")
  internal fun openFontResource(context: Context): InputStream =
    context.resources.openRawResource(R.font.gflex_variable)
}
