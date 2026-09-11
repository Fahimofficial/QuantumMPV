/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.quantummpv.app.domain.playback.Media3PlaybackEngine
import com.quantummpv.app.domain.playback.PlaybackRequest

/**
 * Android-native playback surface used by HybridPlayerActivity when routing to Media3.
 *
 * This activity intentionally keeps its responsibilities narrow. The existing PlayerActivity
 * remains the MPV experience; Media3 is selected only for streams that benefit from Android's
 * native adaptive playback/codec stack.
 */
class Media3PlayerActivity : Activity() {
  private lateinit var engine: Media3PlaybackEngine

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val uri =
      intent.data?.toString()
        ?: intent.getStringExtra(EXTRA_URI)
        ?: intent.getStringExtra("url")
    if (uri.isNullOrBlank()) {
      Toast.makeText(this, "No playable media was supplied", Toast.LENGTH_LONG).show()
      finish()
      return
    }

    val root =
      FrameLayout(this).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    setContentView(root)

    engine = Media3PlaybackEngine(this)
    engine.attach(root)
    engine.prepare(
      PlaybackRequest(
        uri = uri,
        title = intent.getStringExtra(Intent.EXTRA_TITLE),
        mimeType = intent.type,
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L),
        playWhenReady = true,
      ),
    )
  }

  override fun onBackPressed() {
    savePositionAndFinish()
  }

  override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations) savePositionAndFinish()
  }

  private fun savePositionAndFinish() {
    if (!::engine.isInitialized || isFinishing) return
    val position = engine.currentPositionMs()
    setResult(
      RESULT_OK,
      intent.putExtra(EXTRA_RESULT_POSITION_MS, position),
    )
    engine.release()
    finish()
  }

  companion object {
    const val EXTRA_URI = "quantummpv_media3_uri"
    const val EXTRA_START_POSITION_MS = "quantummpv_media3_start_position_ms"
    const val EXTRA_RESULT_POSITION_MS = "quantummpv_media3_result_position_ms"
  }
}
