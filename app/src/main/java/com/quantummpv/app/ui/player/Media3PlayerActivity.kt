/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.quantummpv.app.domain.playback.Media3PlaybackEngine
import com.quantummpv.app.domain.playback.PlaybackRequest

/**
 * Android-native playback surface used by HybridPlayerRouterActivity when routing to Media3.
 *
 * This activity intentionally keeps its responsibilities narrow. The established PlayerActivity
 * remains the MPV experience; Media3 is selected for streams that benefit from Android's native
 * adaptive playback/codec stack or when the user explicitly chooses Media3.
 */
class Media3PlayerActivity : Activity() {
  private lateinit var engine: Media3PlaybackEngine
  private var positionSaved = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val uri =
      intent.data?.toString()
        ?: intent.getStringExtra(EXTRA_URI)
        ?: intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toString()
        ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

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
        headers = intent.getBundleExtra(EXTRA_HEADERS)?.let { bundle ->
          bundle.keySet().associateWith { key -> bundle.getString(key).orEmpty() }.filterValues { it.isNotEmpty() }
        } ?: emptyMap(),
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L),
        playWhenReady = true,
      ),
    )
  }

  override fun onBackPressed() {
    savePositionAndFinish()
  }

  override fun onDestroy() {
    if (::engine.isInitialized && !positionSaved) {
      engine.release()
    }
    super.onDestroy()
  }

  private fun savePositionAndFinish() {
    if (!::engine.isInitialized || positionSaved) return
    positionSaved = true
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
    const val EXTRA_HEADERS = "quantummpv_media3_headers"
    const val EXTRA_START_POSITION_MS = "quantummpv_media3_start_position_ms"
    const val EXTRA_RESULT_POSITION_MS = "quantummpv_media3_result_position_ms"
    const val EXTRA_SOURCE_ENGINE = "quantummpv_media3_source_engine"
  }
}
