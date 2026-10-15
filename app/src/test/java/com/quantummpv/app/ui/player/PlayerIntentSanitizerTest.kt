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
  fun foreignContentUriCannotTraverseIntoPrivateStorage() {
    val file = File(secureFile).apply { parentFile?.mkdirs(); writeText("x") }
    val traversal = Uri.parse("content://attacker.invalid/storage/..${file.absolutePath}")

    assertNull(traversal.resolveLocalPath(context))
    assertNull(traversal.openContentFd(context, allowFdFallback = false))
  }
}
