import sys

# MpvMedia3PlayerTest.kt
mpv_test = """package com.quantummpv.app.ui.player.media3

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvMedia3PlayerTest {

  @Test
  fun testPlaybackStateFor_Error() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ERROR, false)
    assertEquals(Player.STATE_IDLE, result)
  }

  @Test
  fun testPlaybackStateFor_Ready() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.READY, true)
    assertEquals(Player.STATE_READY, result)
  }

  @Test
  fun testPlaybackStateFor_Buffering() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.BUFFERING, true)
    assertEquals(Player.STATE_BUFFERING, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_WithPlaylist() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ENDED, true)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Ended_NoPlaylist() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.ENDED, false)
    assertEquals(Player.STATE_ENDED, result)
  }

  @Test
  fun testPlaybackStateFor_Idle() {
    val result = MpvMedia3Player.playbackStateFor(MpvMedia3PlaybackState.IDLE, true)
    assertEquals(Player.STATE_IDLE, result)
  }
}
"""

with open("app/src/test/java/com/quantummpv/app/ui/player/media3/MpvMedia3PlayerTest.kt", "w") as f:
    f.write(mpv_test)


# UpdateManagerTest.kt
um_test = """package com.quantummpv.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.quantummpv.app.domain.update.AssetSelector.matchesApkVariant

class UpdateManagerTest {

  @Test
  fun testVariantSelection_Standard() {
    assertTrue("QuantumMPV-standard-release.apk".matchesApkVariant("standard"))
    assertTrue("QuantumMPV-foss-release.apk".matchesApkVariant("standard"))
    assertFalse("QuantumMPV-fongmi-release.apk".matchesApkVariant("standard"))
    assertFalse("QuantumMPV-no-vulkan-release.apk".matchesApkVariant("standard"))
  }

  @Test
  fun testVariantSelection_FongMi() {
    assertTrue("QuantumMPV-fongmi-release.apk".matchesApkVariant("fongmi"))
    assertFalse("QuantumMPV-standard-release.apk".matchesApkVariant("fongmi"))
  }

  @Test
  fun testArchSelection_SpecificArchPreferred() {
    val assets = listOf(
      Asset("url", "QuantumMPV-standard-arm64-v8a.apk", 0L, "application/vnd.android.package-archive"),
      Asset("url", "QuantumMPV-standard-universal.apk", 0L, "application/vnd.android.package-archive")
    )
    val best = AssetSelector.selectBestApkAsset(assets, deviceArch = "arm64-v8a", currentVariant = "standard")
    assertEquals("QuantumMPV-standard-arm64-v8a.apk", best?.name)
  }

  @Test
  fun testArchSelection_UniversalFallback() {
    val assets = listOf(
      Asset("url", "QuantumMPV-standard-x86.apk", 0L, "application/vnd.android.package-archive"),
      Asset("url", "QuantumMPV-standard-universal.apk", 0L, "application/vnd.android.package-archive")
    )
    val best = AssetSelector.selectBestApkAsset(assets, deviceArch = "arm64-v8a", currentVariant = "standard")
    assertEquals("QuantumMPV-standard-universal.apk", best?.name)
  }

  @Test
  fun testSelection_NoApks() {
    val assets = listOf(
      Asset("url", "source.zip", 0L, "application/zip"),
      Asset("url", "notes.txt", 0L, "text/plain")
    )
    val best = AssetSelector.selectBestApkAsset(assets, deviceArch = "arm64-v8a", currentVariant = "standard")
    assertNull(best)
  }
}
"""

with open("app/src/test/java/com/quantummpv/app/domain/update/UpdateManagerTest.kt", "w") as f:
    f.write(um_test)

# PlaybackSessionTest.kt
ps_test = """package com.quantummpv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.quantummpv.app.ui.player.PlaybackSessionStateMapper

class PlaybackSessionTest {

  @Test
  fun testResolveEndFileState_EOF() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.EOF,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = null
    )
    assertEquals(PlaybackPhase.IDLE, result.first)
    assertNull(result.second)
  }

  @Test
  fun testResolveEndFileState_Stop() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.STOP,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = null
    )
    assertEquals(PlaybackPhase.IDLE, result.first)
    assertNull(result.second)
  }

  @Test
  fun testResolveEndFileState_Error() {
    val result = PlaybackSessionStateMapper.resolveEndFileState(
      reason = PlaybackSession.EndFileReason.ERROR,
      loadedGeneration = 1L,
      currentGeneration = 1L,
      activeGeneration = 1L,
      parsedError = "Failed to open stream"
    )
    assertEquals(PlaybackPhase.ERROR, result.first)
    assertEquals("Failed to open stream", result.second)
  }
}
"""

with open("app/src/test/java/com/quantummpv/app/ui/player/PlaybackSessionTest.kt", "w") as f:
    f.write(ps_test)

# Now we need to modify UpdateManager and MpvMedia3Player correctly to expose the logic.

# MpvMedia3Player.kt
with open("app/src/main/java/com/quantummpv/app/ui/player/media3/MpvMedia3Player.kt", "r") as f:
    content = f.read()

target = """  private fun playbackStateFor(
    state: MpvMedia3PlaybackState,
    hasPlaylist: Boolean,
  ): Int =
    when {
      state == MpvMedia3PlaybackState.ERROR -> Player.STATE_IDLE
      !hasPlaylist -> if (state == MpvMedia3PlaybackState.ENDED) Player.STATE_ENDED else Player.STATE_IDLE
      state == MpvMedia3PlaybackState.IDLE -> Player.STATE_IDLE
      state == MpvMedia3PlaybackState.BUFFERING -> Player.STATE_BUFFERING
      state == MpvMedia3PlaybackState.READY -> Player.STATE_READY
      else -> Player.STATE_ENDED
    }"""

replacement = """  internal fun playbackStateFor(
    state: MpvMedia3PlaybackState,
    hasPlaylist: Boolean,
  ): Int = Companion.playbackStateFor(state, hasPlaylist)"""

content = content.replace(target, replacement)

target_comp = """  private companion object {
    const val MICROS_PER_MILLI = 1_000L
    const val PROPERTY_POSITION = "time-pos"
    const val PROPERTY_DURATION = "duration"
    const val PROPERTY_SPEED = "speed"
    const val PROPERTY_END_OF_FILE = "eof-reached"
    const val PROPERTY_TITLE = "media-title"
    const val PROPERTY_ARTIST = "metadata/artist"
    const val PROPERTY_ALBUM = "metadata/album"
  }
}"""

replacement_comp = """  internal companion object {
    const val MICROS_PER_MILLI = 1_000L
    const val PROPERTY_POSITION = "time-pos"
    const val PROPERTY_DURATION = "duration"
    const val PROPERTY_SPEED = "speed"
    const val PROPERTY_END_OF_FILE = "eof-reached"
    const val PROPERTY_TITLE = "media-title"
    const val PROPERTY_ARTIST = "metadata/artist"
    const val PROPERTY_ALBUM = "metadata/album"

    internal fun playbackStateFor(
      state: MpvMedia3PlaybackState,
      hasPlaylist: Boolean,
    ): Int =
      when {
        state == MpvMedia3PlaybackState.ERROR -> Player.STATE_IDLE
        !hasPlaylist -> if (state == MpvMedia3PlaybackState.ENDED) Player.STATE_ENDED else Player.STATE_IDLE
        state == MpvMedia3PlaybackState.IDLE -> Player.STATE_IDLE
        state == MpvMedia3PlaybackState.BUFFERING -> Player.STATE_BUFFERING
        state == MpvMedia3PlaybackState.READY -> Player.STATE_READY
        else -> Player.STATE_ENDED
      }
  }
}"""

content = content.replace(target_comp, replacement_comp)

with open("app/src/main/java/com/quantummpv/app/ui/player/media3/MpvMedia3Player.kt", "w") as f:
    f.write(content)


# UpdateManager.kt
with open("app/src/main/java/com/quantummpv/app/domain/update/UpdateManager.kt", "r") as f:
    content = f.read()

target_um = """  private fun selectBestApkAsset(assets: List<Asset>): Asset? {
    val deviceArch = getDeviceArchitecture()
    val compatibleAssets =
      assets.filter { asset ->
        asset.name.startsWith("QuantumMPV-", ignoreCase = true) &&
          asset.name.endsWith(".apk", ignoreCase = true) &&
          asset.name.matchesApkVariant(BuildConfig.UPDATE_APK_VARIANT)
      }

    // First, try to find architecture-specific APK
    val archSpecificApk =
      compatibleAssets.firstOrNull { asset ->
        asset.name.hasAssetToken(deviceArch)
      }

    if (archSpecificApk != null) {
      return archSpecificApk
    }

    // Fallback to universal APK
    val universalApk =
      compatibleAssets.firstOrNull { asset ->
        asset.name.hasAssetToken("universal")
      }

    if (universalApk != null) {
      return universalApk
    }

    // FongMi and No-Vulkan universal assets use the flavor marker instead of "universal".
    return compatibleAssets.firstOrNull { asset ->
      SUPPORTED_ARCHITECTURES.none { architecture -> asset.name.hasAssetToken(architecture) }
    }
  }

  private fun String.matchesApkVariant(variant: String): Boolean {
    val isFongMi = hasAssetToken("fongmi")
    val isNoVulkan = hasAssetToken("no-vulkan")
    return when (variant) {
      "fongmi" -> isFongMi
      "no-vulkan" -> isNoVulkan
      "standard" -> !isFongMi && !isNoVulkan
      else -> false
    }
  }

  private fun String.hasAssetToken(token: String): Boolean =
    Regex(
      pattern = "(?:^|-)${Regex.escape(token)}(?:-|\\\\.apk$)",
      option = RegexOption.IGNORE_CASE,
    ).containsMatchIn(this)

  private fun getDeviceArchitecture(): String {"""

replacement_um = """  private fun selectBestApkAsset(assets: List<Asset>): Asset? =
    AssetSelector.selectBestApkAsset(assets, getDeviceArchitecture(), BuildConfig.UPDATE_APK_VARIANT)

  private fun getDeviceArchitecture(): String {"""

content = content.replace(target_um, replacement_um)

target_comp_um = """  private companion object {
    const val STABLE_RELEASE_URL = "https://api.github.com/repos/Fahimofficial/QuantumMPV/releases/latest"
    const val PREVIEW_RELEASE_URL = "https://fahimofficial.github.io/QuantumMPV/latest.json"
    const val LEGACY_IGNORED_VERSION_KEY = "ignored_version"
    val PREVIEW_TAG_REGEX = Regex(\"\"\"(?:preview-)?r(\\d+)\"\"\", RegexOption.IGNORE_CASE)
    val SUPPORTED_ARCHITECTURES = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
  }
}"""

replacement_comp_um = """  private companion object {
    const val STABLE_RELEASE_URL = "https://api.github.com/repos/Fahimofficial/QuantumMPV/releases/latest"
    const val PREVIEW_RELEASE_URL = "https://fahimofficial.github.io/QuantumMPV/latest.json"
    const val LEGACY_IGNORED_VERSION_KEY = "ignored_version"
    val PREVIEW_TAG_REGEX = Regex(\"\"\"(?:preview-)?r(\\d+)\"\"\", RegexOption.IGNORE_CASE)
    val SUPPORTED_ARCHITECTURES = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
  }
}

internal object AssetSelector {
  fun selectBestApkAsset(
    assets: List<Asset>,
    deviceArch: String,
    currentVariant: String,
  ): Asset? {
    val compatibleAssets =
      assets.filter { asset ->
        asset.name.startsWith("QuantumMPV-", ignoreCase = true) &&
          asset.name.endsWith(".apk", ignoreCase = true) &&
          asset.name.matchesApkVariant(currentVariant)
      }

    val archSpecificApk =
      compatibleAssets.firstOrNull { asset ->
        asset.name.hasAssetToken(deviceArch)
      }

    if (archSpecificApk != null) {
      return archSpecificApk
    }

    val universalApk =
      compatibleAssets.firstOrNull { asset ->
        asset.name.hasAssetToken("universal")
      }

    if (universalApk != null) {
      return universalApk
    }

    return compatibleAssets.firstOrNull { asset ->
      setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").none { architecture -> asset.name.hasAssetToken(architecture) }
    }
  }

  internal fun String.matchesApkVariant(variant: String): Boolean {
    val isFongMi = hasAssetToken("fongmi")
    val isNoVulkan = hasAssetToken("no-vulkan")
    return when (variant) {
      "fongmi" -> isFongMi
      "no-vulkan" -> isNoVulkan
      "standard" -> !isFongMi && !isNoVulkan
      else -> false
    }
  }

  internal fun String.hasAssetToken(token: String): Boolean =
    Regex(
      pattern = "(?:^|-)${Regex.escape(token)}(?:-|\\\\.apk$)",
      option = RegexOption.IGNORE_CASE,
    ).containsMatchIn(this)
}"""

content = content.replace(target_comp_um, replacement_comp_um)

with open("app/src/main/java/com/quantummpv/app/domain/update/UpdateManager.kt", "w") as f:
    f.write(content)
