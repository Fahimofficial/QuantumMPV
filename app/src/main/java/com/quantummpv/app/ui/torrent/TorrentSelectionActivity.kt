/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.torrent

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.quantummpv.app.database.repository.NetworkStreamEntryRepository
import com.quantummpv.app.domain.torrent.TorrentStreamingEngine
import com.quantummpv.app.repository.wyzie.WyzieSearchRepository
import com.quantummpv.app.ui.player.InternalPlayerLaunch
import com.quantummpv.app.ui.player.PlayerActivity
import com.quantummpv.app.ui.theme.MpvrxTheme
import com.quantummpv.app.utils.media.MediaUtils
import org.koin.android.ext.android.inject

class TorrentSelectionActivity : AppCompatActivity() {
  private val torrentStreamingEngine: TorrentStreamingEngine by inject()
  private val streamEntryRepository: NetworkStreamEntryRepository by inject()
  private val wyzieSearchRepository: WyzieSearchRepository by inject()
  private val viewModel: TorrentSelectionViewModel by viewModels {
    TorrentSelectionViewModel.factory(
      torrentStreamingEngine = torrentStreamingEngine,
      streamEntryRepository = streamEntryRepository,
      wyzieSearchRepository = wyzieSearchRepository,
    )
  }

  private var playerLaunched = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val source = extractTorrentSource(intent)
    if (source.isNullOrBlank()) {
      finishWithoutAnimation()
      return
    }

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = closePicker()
      },
    )

    // Links from browsers and other apps only connect after the user agrees; picks made inside the
    // app (Network tab, saved media) carry the in-process launch token and open right away.
    val requireConsent = !InternalPlayerLaunch.isInternal(intent)
    viewModel.initialize(
      requireConsent = requireConsent,
      value =
      TorrentSelectionInput(
        source = source,
        title =
          intent.getStringExtra(MediaUtils.EXTRA_MEDIA_TITLE)
            ?: intent.getStringExtra("title")
            ?: intent.getStringExtra("introdb_title"),
        description =
          intent.getStringExtra(MediaUtils.EXTRA_MEDIA_DESCRIPTION)
            ?: intent.getStringExtra("description")
            ?: intent.getStringExtra("overview"),
        posterUrl =
          intent.getStringExtra(MediaUtils.EXTRA_MEDIA_POSTER_URL)
            ?: intent.getStringExtra("poster")
            ?: intent.getStringExtra("poster_url"),
        backdropUrl =
          intent.getStringExtra(MediaUtils.EXTRA_MEDIA_BACKDROP_URL)
            ?: intent.getStringExtra("backdrop")
            ?: intent.getStringExtra("backdrop_url"),
      ),
    )

    setContent {
      val state by viewModel.uiState.collectAsState()
      LaunchedEffect(viewModel) {
        viewModel.launches.collect(::openPlayer)
      }
      MpvrxTheme {
        TorrentSelectionScreen(
          state = state,
          onBack = ::closePicker,
          onRetry = viewModel::retry,
          onSelect = viewModel::select,
          onConfirmConsent = viewModel::confirmConsent,
        )
      }
    }
  }

  private fun openPlayer(request: TorrentSelectionLaunch) {
    if (playerLaunched || isFinishing) return
    playerLaunched = true
    // A fresh intent: only the fields the player needs, marked as an in-process launch so the
    // prepared torrent id survives the player's external-intent sanitizer. Extras from the
    // original (possibly external) intent are deliberately not forwarded under that token.
    val playbackIntent =
      Intent().apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(request.source)
        setClass(this@TorrentSelectionActivity, PlayerActivity::class.java)
        putExtra("internal_launch", true)
        InternalPlayerLaunch.mark(this)
        listOf(
          MediaUtils.EXTRA_MEDIA_DESCRIPTION,
          MediaUtils.EXTRA_MEDIA_POSTER_URL,
          MediaUtils.EXTRA_MEDIA_BACKDROP_URL,
          "introdb_title",
          "introdb_tmdb_id",
          "introdb_imdb_id",
          "introdb_season",
          "introdb_episode",
          "introdb_media_type",
        ).forEach { key ->
          @Suppress("DEPRECATION")
          when (val value = intent.extras?.get(key)) {
            is String -> putExtra(key, value)
            is Int -> putExtra(key, value)
            is Long -> putExtra(key, value)
          }
        }
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra("title", request.file.name)
        putExtra(MediaUtils.EXTRA_MEDIA_TITLE, request.file.name)
        putExtra(MediaUtils.EXTRA_TORRENT_SOURCE, request.source)
        putExtra(MediaUtils.EXTRA_TORRENT_FILE_INDEX, request.file.index)
        putExtra(MediaUtils.EXTRA_TORRENT_PREPARATION_ID, request.preparationId)
        putExtra("is_audio", request.file.mimeType.startsWith("audio/"))
      }
    startActivity(playbackIntent)
    finishWithoutAnimation()
  }

  private fun closePicker() {
    if (!playerLaunched) viewModel.cancel()
    finishWithoutAnimation()
  }

  @Suppress("DEPRECATION")
  private fun finishWithoutAnimation() {
    finish()
    overridePendingTransition(0, 0)
  }

  private fun extractTorrentSource(intent: Intent?): String? {
    intent ?: return null
    intent.getStringExtra(MediaUtils.EXTRA_TORRENT_SOURCE)?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    intent.dataString?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    if (intent.action == Intent.ACTION_SEND) {
      val stream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
      stream?.toString()?.takeIf(String::isNotBlank)?.let { return it }
      intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    }
    return null
  }
}
