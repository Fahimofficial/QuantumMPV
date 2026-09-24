package com.quantummpv.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManagerTest {

  // Dummy Asset class for testing
  data class Asset(val name: String, val downloadUrl: String)

  // Helper method to bypass instantiating UpdateManager (which requires Context and HTTP client)
  private fun selectBestApkAsset(
    assets: List<Asset>,
    deviceArch: String = "arm64-v8a",
    apkVariant: String = "standard",
  ): Asset? {
    val compatibleAssets =
      assets.filter { asset ->
        asset.name.startsWith("QuantumMPV-", ignoreCase = true) &&
          asset.name.endsWith(".apk", ignoreCase = true) &&
          asset.name.matchesApkVariant(apkVariant)
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
      listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").none { architecture -> asset.name.hasAssetToken(architecture) }
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
      pattern = "(?:^|-)${Regex.escape(token)}(?:-|\\.apk$)",
      option = RegexOption.IGNORE_CASE,
    ).containsMatchIn(this)

  @Test
  fun testSelectBestApkAsset_StandardUniversal() {
    val assets =
      listOf(
        Asset("QuantumMPV-fongmi-v1.0.apk", "url"),
        Asset("QuantumMPV-v1.0.apk", "url"),
      )
    val best = selectBestApkAsset(assets, "arm64-v8a", "standard")
    assertEquals("QuantumMPV-v1.0.apk", best?.name)
  }

  @Test
  fun testSelectBestApkAsset_FongmiArch() {
    val assets =
      listOf(
        Asset("QuantumMPV-fongmi-arm64-v8a-v1.0.apk", "url"),
        Asset("QuantumMPV-fongmi-v1.0.apk", "url"),
        Asset("QuantumMPV-v1.0.apk", "url"),
      )
    val best = selectBestApkAsset(assets, "arm64-v8a", "fongmi")
    assertEquals("QuantumMPV-fongmi-arm64-v8a-v1.0.apk", best?.name)
  }
}
