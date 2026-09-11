/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.quantummpv.app.domain.playback.PlaybackEnginePreferences
import com.quantummpv.app.domain.playback.PlaybackEngineRouter
import com.quantummpv.app.domain.playback.PlaybackEngineType
import org.koin.android.ext.android.inject

/**
 * Thin entry-point router for externally launched media.
 *
 * Keeping routing here means the established PlayerActivity remains untouched for MPV playback,
 * while adaptive streams can enter the Android-native Media3 activity without initializing libmpv.
 */
class HybridPlayerRouterActivity : Activity() {
  private val enginePreferences: PlaybackEnginePreferences by inject()
  private val engineRouter = PlaybackEngineRouter()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val uri =
      intent.data?.toString()
        ?: intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toString()
        ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    if (uri.isNullOrBlank()) {
      startActivity(forwardToPlayer())
      finish()
      return
    }

    val preference =
      intent.getStringExtra(EXTRA_ENGINE)?.let { encoded ->
        when (encoded.lowercase()) {
          "mpv" -> PlaybackEngineRouter.Preference.MPV
          "media3" -> PlaybackEngineRouter.Preference.MEDIA3
          else -> PlaybackEngineRouter.Preference.AUTO
        }
      } ?: enginePreferences.get()

    val selected = engineRouter.preferredEngine(uri, intent.type, preference)
    when (selected) {
      PlaybackEngineType.MEDIA3 -> {
        startActivity(
          Intent(this, Media3PlayerActivity::class.java).apply {
            data = uri.toUri()
            type = intent.type
            putExtra(Intent.EXTRA_TITLE, intent.getStringExtra(Intent.EXTRA_TITLE))
            putExtra(Media3PlayerActivity.EXTRA_START_POSITION_MS, intent.getLongExtra(Media3PlayerActivity.EXTRA_START_POSITION_MS, 0L))
            putExtra(Media3PlayerActivity.EXTRA_SOURCE_ENGINE, "media3-auto")
          },
        )
      }
      PlaybackEngineType.MPV -> {
        startActivity(forwardToPlayer())
      }
    }
    finish()
  }

  private fun forwardToPlayer(): Intent =
    Intent(this, PlayerActivity::class.java).apply {
      action = intent.action
      data = intent.data
      type = intent.type
      putExtras(intent)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  companion object {
    const val EXTRA_ENGINE = "quantummpv_playback_engine"
  }
}
