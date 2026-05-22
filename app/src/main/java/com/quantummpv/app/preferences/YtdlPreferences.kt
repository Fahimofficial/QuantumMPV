package com.quantummpv.app.preferences

import com.quantummpv.app.preferences.preference.PreferenceStore

class YtdlPreferences(
  preferenceStore: PreferenceStore,
) {
  val ytdlFormat = preferenceStore.getString("video_ytdl_format", "")
  val ytdlQuality = preferenceStore.getInt("ytdl_quality", -1) // -1 for any
  val preferH264 = preferenceStore.getBoolean("ytdl_prefer_h264", false)

  // Subtitles and advanced configurations
  val writeSubs = preferenceStore.getBoolean("ytdl_write_subs", true)
  val writeAutoSubs = preferenceStore.getBoolean("ytdl_write_auto_subs", false)
  val customUserAgent = preferenceStore.getString("ytdl_custom_user_agent", "")
  val customRawOptions = preferenceStore.getString("ytdl_custom_raw_options", "")
}
