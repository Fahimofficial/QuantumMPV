/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import android.content.SharedPreferences

/**
 * Small persistence boundary for the engine choice. This keeps the initial integration independent
 * of the large player preference surface and is safe to migrate later into PreferenceStore.
 */
class PlaybackEnginePreferences(
  private val preferences: SharedPreferences,
) {
  companion object {
    private const val KEY = "playback_engine"
    private const val AUTO = "auto"
    private const val MPV = "mpv"
    private const val MEDIA3 = "media3"
  }

  fun get(): PlaybackEngineRouter.Preference =
    when (preferences.getString(KEY, AUTO)) {
      MPV -> PlaybackEngineRouter.Preference.MPV
      MEDIA3 -> PlaybackEngineRouter.Preference.MEDIA3
      else -> PlaybackEngineRouter.Preference.AUTO
    }

  fun set(value: PlaybackEngineRouter.Preference) {
    val encoded =
      when (value) {
        PlaybackEngineRouter.Preference.AUTO -> AUTO
        PlaybackEngineRouter.Preference.MPV -> MPV
        PlaybackEngineRouter.Preference.MEDIA3 -> MEDIA3
      }
    preferences.edit().putString(KEY, encoded).apply()
  }
}
