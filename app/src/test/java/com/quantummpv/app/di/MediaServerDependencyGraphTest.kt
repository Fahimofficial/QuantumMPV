package com.quantummpv.app.di

import android.content.Context
import com.quantummpv.app.data.audiobookshelf.AudiobookshelfClient
import com.quantummpv.app.database.dao.AudiobookDao
import com.quantummpv.app.database.dao.AudiobookshelfServerDao
import com.quantummpv.app.database.dao.PlaybackBookmarkDao
import com.quantummpv.app.repository.AudiobookshelfRepository
import com.quantummpv.app.repository.JellyfinRepository
import com.quantummpv.app.repository.NavidromeRepository
import com.quantummpv.app.repository.SeerrRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Media servers screen and the Audiobooks tab create AudiobookshelfViewModel, whose injected
 * repository, client and DAOs must exist in the real Koin graph. They were missing after the
 * v2.6.0 port, so opening Media servers (e.g. Jellyfin → Configure) threw NoDefinitionFoundException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaServerDependencyGraphTest {
  @Before
  fun setUp() {
    runCatching { stopKoin() }
    val context: Context = RuntimeEnvironment.getApplication()
    startKoin {
      androidContext(context)
      modules(PreferencesModule, DatabaseModule, FileManagerModule, domainModule, DownloadModule)
    }
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun mediaServerScreensResolveEveryInjectedDependency() {
    val koin = GlobalContext.get()
    assertNotNull(koin.get<JellyfinRepository>())
    assertNotNull(koin.get<NavidromeRepository>())
    assertNotNull(koin.get<SeerrRepository>())
    assertNotNull(koin.get<AudiobookshelfServerDao>())
    assertNotNull(koin.get<AudiobookshelfClient>())
    assertNotNull(koin.get<AudiobookshelfRepository>())
    assertNotNull(koin.get<AudiobookDao>())
    assertNotNull(koin.get<PlaybackBookmarkDao>())
  }

  /**
   * Every type the app resolves from Koin by class must be defined, or its screen crashes on open.
   * GitHubContributorsRepository is excluded: its only consumer, AboutContributorsSection, is never
   * composed. Register it before wiring that section into the About screen.
   */
  @Test
  fun everyInjectedTypeIsDefined() {
    val koin = GlobalContext.get()
    val failures = mutableListOf<String>()

    fun probe(
      name: String,
      resolve: () -> Any,
    ) {
      runCatching(resolve).onFailure { error ->
        failures += "$name: $error"
      }
    }
    probe("AdvancedPreferences") { koin.get<com.quantummpv.app.preferences.AdvancedPreferences>() }
    probe("AiPreferences") { koin.get<com.quantummpv.app.preferences.AiPreferences>() }
    probe("AiService") { koin.get<com.quantummpv.app.repository.ai.AiService>() }
    probe("AndroidCookieJar") { koin.get<com.quantummpv.app.network.AndroidCookieJar>() }
    probe("AppDownloadManager") { koin.get<com.quantummpv.app.domain.download.AppDownloadManager>() }
    probe("AppearancePreferences") { koin.get<com.quantummpv.app.preferences.AppearancePreferences>() }
    probe("AudiobookDao") { koin.get<com.quantummpv.app.database.dao.AudiobookDao>() }
    probe("AudiobookshelfClient") { koin.get<com.quantummpv.app.data.audiobookshelf.AudiobookshelfClient>() }
    probe("AudiobookshelfRepository") { koin.get<com.quantummpv.app.repository.AudiobookshelfRepository>() }
    probe("AudioPreferences") { koin.get<com.quantummpv.app.preferences.AudioPreferences>() }
    probe("BrowserPreferences") { koin.get<com.quantummpv.app.preferences.BrowserPreferences>() }
    probe("DecoderPreferences") { koin.get<com.quantummpv.app.preferences.DecoderPreferences>() }
    probe("DownloadLocations") { koin.get<com.quantummpv.app.domain.download.DownloadLocations>() }
    probe("FileManager") { koin.get<com.github.k1rakishou.fsaf.FileManager>() }
    probe("FoldersPreferences") { koin.get<com.quantummpv.app.preferences.FoldersPreferences>() }
    probe("GesturePreferences") { koin.get<com.quantummpv.app.preferences.GesturePreferences>() }
    probe("HdrToysManager") { koin.get<com.quantummpv.app.domain.hdr.HdrToysManager>() }
    probe("IntroDbRepository") { koin.get<com.quantummpv.app.repository.IntroDbRepository>() }
    probe("JellyfinRepository") { koin.get<com.quantummpv.app.repository.JellyfinRepository>() }
    probe("Json") { koin.get<kotlinx.serialization.json.Json>() }
    probe("MediaIndexDao") { koin.get<com.quantummpv.app.database.dao.MediaIndexDao>() }
    probe("MediaServerPreferences") { koin.get<com.quantummpv.app.preferences.MediaServerPreferences>() }
    probe("MpvRxDatabase") { koin.get<com.quantummpv.app.database.MpvRxDatabase>() }
    probe("MpvRxSubtitleHubRepository") { koin.get<com.quantummpv.app.repository.subtitlehub.MpvRxSubtitleHubRepository>() }
    probe("NavidromeClient") { koin.get<com.quantummpv.app.data.navidrome.NavidromeClient>() }
    probe("NavidromeRepository") { koin.get<com.quantummpv.app.repository.NavidromeRepository>() }
    probe("NetworkBookmarkPreferences") { koin.get<com.quantummpv.app.preferences.NetworkBookmarkPreferences>() }
    probe("NetworkRepository") { koin.get<com.quantummpv.app.repository.NetworkRepository>() }
    probe("NetworkStreamEntryRepository") { koin.get<com.quantummpv.app.database.repository.NetworkStreamEntryRepository>() }
    probe("OkHttpClient") { koin.get<okhttp3.OkHttpClient>() }
    probe("OnlineSubtitleOrchestrator") { koin.get<com.quantummpv.app.repository.subtitle.OnlineSubtitleOrchestrator>() }
    probe("PlaybackStateRepository") { koin.get<com.quantummpv.app.domain.playbackstate.repository.PlaybackStateRepository>() }
    probe("PlayerPreferences") { koin.get<com.quantummpv.app.preferences.PlayerPreferences>() }
    probe("PlaylistRepository") { koin.get<com.quantummpv.app.database.repository.PlaylistRepository>() }
    probe("RecentlyPlayedRepository") { koin.get<com.quantummpv.app.domain.recentlyplayed.repository.RecentlyPlayedRepository>() }
    probe("SecureFolderPreferences") { koin.get<com.quantummpv.app.preferences.SecureFolderPreferences>() }
    probe("SecureFolderRepository") { koin.get<com.quantummpv.app.database.repository.SecureFolderRepository>() }
    probe("SeerrPreferences") { koin.get<com.quantummpv.app.preferences.SeerrPreferences>() }
    probe("SeerrRepository") { koin.get<com.quantummpv.app.repository.SeerrRepository>() }
    probe("SettingsManager") { koin.get<com.quantummpv.app.preferences.SettingsManager>() }
    probe("SubtitleGenerationService") { koin.get<com.quantummpv.app.repository.ai.SubtitleGenerationService>() }
    probe("SubtitlesPreferences") { koin.get<com.quantummpv.app.preferences.SubtitlesPreferences>() }
    probe("ThumbnailRepository") { koin.get<com.quantummpv.app.domain.thumbnail.ThumbnailRepository>() }
    probe("TorrentStreamingEngine") { koin.get<com.quantummpv.app.domain.torrent.TorrentStreamingEngine>() }
    probe("VideoMetadataCacheRepository") { koin.get<com.quantummpv.app.database.repository.VideoMetadataCacheRepository>() }
    probe("WyzieSearchRepository") { koin.get<com.quantummpv.app.repository.wyzie.WyzieSearchRepository>() }
    probe("YtdlpDownloadEngine") { koin.get<com.quantummpv.app.domain.download.YtdlpDownloadEngine>() }
    probe("YtdlPreferences") { koin.get<com.quantummpv.app.preferences.YtdlPreferences>() }
    org.junit.Assert.assertTrue("Koin resolution failures: $failures", failures.isEmpty())
  }
}
