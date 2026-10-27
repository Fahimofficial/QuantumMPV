package com.quantummpv.app.ui.torrent

import android.os.Looper
import com.quantummpv.app.database.repository.NetworkStreamEntryRepository
import com.quantummpv.app.domain.torrent.TorrentStreamingEngine
import com.quantummpv.app.repository.wyzie.WyzieSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TorrentConsentTest {
  private val engine = mockk<TorrentStreamingEngine>(relaxed = true)

  private fun viewModel() =
    TorrentSelectionViewModel(
      torrentStreamingEngine = engine,
      streamEntryRepository = mockk<NetworkStreamEntryRepository>(relaxed = true),
      wyzieSearchRepository = mockk<WyzieSearchRepository>(relaxed = true),
    )

  private val magnet =
    "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Big%20Buck%20Bunny&tr=udp%3A%2F%2Ftracker.example%3A80"

  @Test
  fun externalLinkWaitsForConsentBeforeTouchingTheNetwork() {
    coEvery { engine.prepareTorrent(any()) } throws IllegalStateException("offline in test")
    val viewModel = viewModel()

    viewModel.initialize(TorrentSelectionInput(source = magnet), requireConsent = true)
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(TorrentSelectionUiState.AwaitingConsent("Big Buck Bunny"), viewModel.uiState.value)
    coVerify(exactly = 0) { engine.prepareTorrent(any()) }

    viewModel.confirmConsent()
    shadowOf(Looper.getMainLooper()).idle()

    coVerify(exactly = 1) { engine.prepareTorrent(magnet) }
    assertTrue(viewModel.uiState.value !is TorrentSelectionUiState.AwaitingConsent)
  }

  @Test
  fun inAppPicksStartImmediately() {
    coEvery { engine.prepareTorrent(any()) } throws IllegalStateException("offline in test")
    val viewModel = viewModel()

    viewModel.initialize(TorrentSelectionInput(source = magnet, title = "Saved"), requireConsent = false)
    shadowOf(Looper.getMainLooper()).idle()

    coVerify(exactly = 1) { engine.prepareTorrent(magnet) }
  }
}
