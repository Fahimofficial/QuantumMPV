/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences

import android.content.SharedPreferences

/**
 * Lets new installs get better defaults without changing anything for people who already use the app.
 *
 * A preference that was never written follows whatever default the code declares, so changing a default in
 * code would silently change the app for every existing user who never touched that setting. Each
 * generation below records the defaults it replaced. On first start after upgrading, an existing install
 * gets those old values written explicitly for every key it has not stored, so it keeps behaving exactly as
 * before; a fresh install skips straight to the current generation and sees the new defaults.
 */
internal object PreferenceDefaultsGeneration {
  const val KEY = "defaults_generation"

  /** Installs from before this mechanism existed. */
  private const val LEGACY = 1

  /** Defaults that generation `n` replaced, keyed by the generation that introduced the new value. */
  private val replacedDefaults: Map<Int, Map<String, Any>> =
    mapOf(
      2 to
        mapOf(
          // Recommended player HUD (fewer buttons, no scroll row) and no controls drawer.
          "top_right_controls" to "CAST,CURRENT_CHAPTER,DECODER,AUDIO_TRACK,SUBTITLES,MORE_OPTIONS",
          "bottom_right_controls" to "FRAME_NAVIGATION,CLIP,SCOPES,VIDEO_ZOOM,PICTURE_IN_PICTURE,ASPECT_RATIO",
          "bottom_left_controls" to
            "BACKGROUND_PLAYBACK,LOCK_CONTROLS,SCREEN_ROTATION,PLAYBACK_SPEED,REPEAT_MODE,SHUFFLE,AB_LOOP",
          "portrait_bottom_controls" to
            "CAST,SCREEN_ROTATION,DECODER,AUDIO_TRACK,SUBTITLES,BOOKMARKS_CHAPTERS,PLAYBACK_SPEED," +
            "BACKGROUND_PLAYBACK,REPEAT_MODE,SHUFFLE,VIDEO_ZOOM,FRAME_NAVIGATION,CLIP,SCOPES,ASPECT_RATIO," +
            "PICTURE_IN_PICTURE,LOCK_CONTROLS,MORE_OPTIONS",
          "show_controls_drawer" to true,
        ),
    )

  /** Flags of migrations that no longer run; removed so they stop appearing in settings backups. */
  private val retiredKeys = listOf("cast_button_migration_complete", "clip_button_migration_complete")

  val CURRENT: Int = replacedDefaults.keys.max()

  /** Old defaults an existing install keeps, in case a caller needs to show or test them. */
  fun legacyDefault(key: String): Any? =
    replacedDefaults.entries
      .sortedBy { it.key }
      .firstNotNullOfOrNull { (_, defaults) -> defaults[key] }

  /**
   * Brings [preferences] to [CURRENT]. [hasOtherAppData] reports data outside these preferences (for
   * example the media database) so an install whose preferences are empty but which has been used before
   * is still treated as existing.
   *
   * @return the generation the preferences were at before this call.
   */
  fun apply(
    preferences: SharedPreferences,
    hasOtherAppData: Boolean,
  ): Int {
    val stored = preferences.all
    val from =
      when (val value = stored[KEY]) {
        is Int -> value
        else -> if (stored.isEmpty() && !hasOtherAppData) CURRENT else LEGACY
      }
    if (from >= CURRENT && KEY in stored) return from

    val editor = preferences.edit()
    replacedDefaults
      .filterKeys { it > from }
      .toSortedMap()
      .values
      .forEach { defaults ->
        defaults.forEach { (key, value) ->
          if (key !in stored) editor.put(key, value)
        }
      }
    retiredKeys.forEach(editor::remove)
    editor.putInt(KEY, CURRENT)
    editor.apply()
    return from
  }

  private fun SharedPreferences.Editor.put(
    key: String,
    value: Any,
  ) {
    when (value) {
      is String -> putString(key, value)
      is Boolean -> putBoolean(key, value)
      is Int -> putInt(key, value)
      is Long -> putLong(key, value)
      is Float -> putFloat(key, value)
      else -> error("Unsupported default type for $key")
    }
  }
}
