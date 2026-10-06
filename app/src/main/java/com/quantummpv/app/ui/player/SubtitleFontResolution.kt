/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import com.quantummpv.app.preferences.DEFAULT_SUBTITLE_FONT_FAMILY
import com.quantummpv.app.preferences.SubtitlesPreferences
import com.quantummpv.app.ui.player.MpvOsdFont

/**
 * Resolves the font family passed to mpv for primary and secondary subtitles.
 * Official mpv has no secondary subtitle font property; secondary subtitles inherit sub-font.
 *
 * The default "sans-serif" font family may not resolve on Android's fontconfig without system
 * font aliases configured. Fall back to the bundled "Google Sans Flex" font which is guaranteed
 * to be installed and supports a wide Unicode range.
 */
fun resolveSubtitleFontFamily(explicitFont: String): String =
  explicitFont.takeUnless {
    it.isBlank() || it == DEFAULT_SUBTITLE_FONT_FAMILY
  } ?: MpvOsdFont.FAMILY

fun resolveSubtitleFontFamily(subtitlesPreferences: SubtitlesPreferences): String =
  resolveSubtitleFontFamily(subtitlesPreferences.font.get())
