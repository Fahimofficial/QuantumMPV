package com.quantummpv.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlayerIntentSanitizerTest {
  private lateinit var context: Context
  private lateinit var secureFile: String

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    secureFile = File(context.filesDir, "secure_media/holiday.mp4").absolutePath
  }

  @Test
  fun policyRecognisesPrivateStorageIncludingTraversalAndLegacyDataPaths() {
    val policy = PrivateStoragePolicy("com.example.app", listOf(File("/srv/app-data")))

    assertTrue(policy.isPrivatePath("/srv/app-data/files/secure_media/a.mp4"))
    assertTrue(policy.isPrivatePath("/storage/emulated/0/../../../srv/app-data/files/a.mp4"))
    assertTrue(policy.isPrivatePath("/data/data/com.example.app/files/a.mp4"))
    assertTrue(policy.isPrivatePath("/data/user/10/com.example.app/cache/a.mp4"))
    assertFalse(policy.isPrivatePath("/data/data/com.example.app.other/files/a.mp4"))
    assertFalse(policy.isPrivatePath("/storage/emulated/0/Movies/a.mp4"))
    assertTrue(policy.isPrivateUri(Uri.parse("content://com.example.app.provider/updates/x.apk")))
    assertFalse(policy.isPrivateUri(Uri.parse("content://media/external/video/media/12")))
    assertFalse(policy.isPrivateUri(Uri.parse("https://example.com/video.mp4")))
  }

  @Test
  fun externalIntentCannotOpenSecureFolderMedia() {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.fromFile(File(secureFile)))
        .putExtra("local_media_path", secureFile)
        .putExtra("uri", "file://$secureFile")
        .putExtra("internal_launch", true)

    val sanitized = PlayerIntentSanitizer.sanitize(context, intent)

    assertNull(sanitized.data)
    assertNull(sanitized.getStringExtra("local_media_path"))
    assertNull(sanitized.getStringExtra("uri"))
  }

  @Test
  fun externalIntentKeepsPublicMediaAndDropsOnlyPrivateListEntries() {
    val publicUri = Uri.parse("content://media/external/video/media/7")
    val intent =
      Intent(Intent.ACTION_VIEW, publicUri)
        .putParcelableArrayListExtra("playlist", arrayListOf(publicUri, Uri.fromFile(File(secureFile))))
        .putExtra("subs", arrayOf<android.os.Parcelable>(Uri.parse("https://example.com/a.srt"), Uri.fromFile(File(secureFile))))
        .putStringArrayListExtra("video_list", arrayListOf("/storage/emulated/0/a.mkv", secureFile))

    val sanitized = PlayerIntentSanitizer.sanitize(context, intent)

    assertEquals(publicUri, sanitized.data)
    @Suppress("DEPRECATION")
    assertEquals(listOf(publicUri), sanitized.getParcelableArrayListExtra<Uri>("playlist"))
    @Suppress("DEPRECATION")
    assertEquals(1, sanitized.getParcelableArrayExtra("subs")?.size)
    assertEquals(listOf("/storage/emulated/0/a.mkv"), sanitized.getStringArrayListExtra("video_list"))
  }

  @Test
  fun inProcessLaunchKeepsPrivatePathsAndForgedTokensDoNot() {
    val internal = InternalPlayerLaunch.mark(Intent(Intent.ACTION_VIEW).putExtra("local_media_path", secureFile))
    assertEquals(secureFile, PlayerIntentSanitizer.sanitize(context, internal).getStringExtra("local_media_path"))

    val forged =
      Intent(Intent.ACTION_VIEW)
        .putExtra("local_media_path", secureFile)
        .putExtra(InternalPlayerLaunch.EXTRA_TOKEN, "00000000000000000000000000000000")
    assertNull(PlayerIntentSanitizer.sanitize(context, forged).getStringExtra("local_media_path"))
  }

  @Test
  fun sharedTextPointingAtPrivateStorageIsDropped() {
    val intent =
      Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "look file://$secureFile")

    assertNull(PlayerIntentSanitizer.sanitize(context, intent).getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun pseudoProtocolsThatWrapLocalPathsAreDroppedFromExternalIntents() {
    val wrapped =
      listOf(
        "edl://%${secureFile.length}%$secureFile",
        "av://file:$secureFile",
        "lavf://file:$secureFile",
        "slice://0-100@$secureFile",
        "fd://3",
        "archive://$secureFile|/inner.mkv",
        "memory://raw",
        "mpvrx-network://1/Movies/a.mkv",
        "mpvrx-xtream://account/stream/1",
      )
    wrapped.forEach { value ->
      val intent =
        Intent("com.example.PLAY")
          .putExtra("uri", value)
          .putStringArrayListExtra("video_list", arrayListOf(value, "https://example.com/ok.mp4"))
      val sanitized = PlayerIntentSanitizer.sanitize(context, intent)
      assertNull("uri extra $value must be removed", sanitized.getStringExtra("uri"))
      assertEquals(listOf("https://example.com/ok.mp4"), sanitized.getStringArrayListExtra("video_list"))

      val view = PlayerIntentSanitizer.sanitize(context, Intent(Intent.ACTION_VIEW, Uri.parse(value)))
      assertNull("data $value must be removed", view.data)
    }
  }

  @Test
  fun ordinaryStreamingAndFileSchemesStayAllowed() {
    listOf(
      "https://cdn.example.com/a.m3u8",
      "rtsp://camera.local/stream",
      "rtmp://live.example.com/app/key",
      "udp://@239.0.0.1:1234",
      "srt://example.com:9000",
      "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
      "content://media/external/video/media/3",
      "file:///storage/emulated/0/Movies/a.mkv",
    ).forEach { value ->
      val sanitized = PlayerIntentSanitizer.sanitize(context, Intent(Intent.ACTION_VIEW, Uri.parse(value)))
      assertEquals(value, sanitized.dataString)
    }
  }

  @Test
  fun externalIntentsCannotUseSavedConnectionsPlaylistsOrStateKeys() {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/a.mp4"))
        .putExtra("network_connection_id", 4L)
        .putExtra("network_file_path", "/Private/b.mkv")
        .putExtra("network_playlist_connection_id", 4L)
        .putStringArrayListExtra("network_playlist_paths", arrayListOf("/Private/c.mkv"))
        .putExtra("playlist_id", 9L)
        .putExtra("torrent_preparation_id", "prep")
        .putExtra("media_identifier", "media:v2:someone-elses-file")
        .putExtra("title", "Shown title")

    val sanitized = PlayerIntentSanitizer.sanitize(context, intent)

    listOf(
      "network_connection_id",
      "network_file_path",
      "network_playlist_connection_id",
      "network_playlist_paths",
      "playlist_id",
      "torrent_preparation_id",
      "media_identifier",
    ).forEach { key -> assertFalse("$key must be removed", sanitized.hasExtra(key)) }
    assertEquals("Shown title", sanitized.getStringExtra("title"))

    val internal = InternalPlayerLaunch.mark(Intent(Intent.ACTION_VIEW).putExtra("network_connection_id", 4L))
    assertEquals(4L, PlayerIntentSanitizer.sanitize(context, internal).getLongExtra("network_connection_id", -1L))
  }

  @Test
  fun foreignContentUriCannotTraverseIntoPrivateStorage() {
    val file = File(secureFile).apply { parentFile?.mkdirs(); writeText("x") }
    val traversal = Uri.parse("content://attacker.invalid/storage/..${file.absolutePath}")

    assertNull(traversal.resolveLocalPath(context))
    assertNull(traversal.openContentFd(context, allowFdFallback = false))
  }
}
