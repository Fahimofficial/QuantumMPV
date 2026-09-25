package com.quantummpv.app.domain.update

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
