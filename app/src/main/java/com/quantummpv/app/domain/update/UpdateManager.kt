/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.domain.update

import android.content.Context
import android.os.Build
import com.quantummpv.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class UpdateManager(
  private val context: Context,
) {
  private val client = OkHttpClient()
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun checkForUpdate(
    channel: AppUpdateChannel,
    forceShow: Boolean = false,
  ): Release? {
    // Return null immediately if update checks are disabled for this build.
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
      return null
    }

    val release =
      getLatestRelease(
        when (channel) {
          AppUpdateChannel.STABLE -> STABLE_RELEASE_URL
          AppUpdateChannel.PREVIEW -> PREVIEW_RELEASE_URL
        },
      )
    if (selectBestApkAsset(release.assets) == null) {
      return null
    }
    val prefs = context.getSharedPreferences("mpvrx_prefs", Context.MODE_PRIVATE)
    val ignoredVersion =
      prefs.getString(ignoredVersionKey(channel), null)
        ?: if (channel == AppUpdateChannel.STABLE) prefs.getString(LEGACY_IGNORED_VERSION_KEY, null) else null

    // If this version was ignored, don't show it unless forced (manual check)
    val isIgnored =
      ignoredVersion == release.tagName ||
        (channel == AppUpdateChannel.STABLE && ignoredVersion == release.tagName.removePrefix("v"))
    if (!forceShow && isIgnored) {
      return null
    }

    val isNewer =
      when (channel) {
        AppUpdateChannel.STABLE -> {
          val currentVersion = BuildConfig.VERSION_NAME.substringBefore('-')
          isNewerVersion(release.tagName.removePrefix("v"), currentVersion)
        }
        AppUpdateChannel.PREVIEW -> {
          val remoteCommitCount = release.commitCount ?: parsePreviewCommitCount(release.tagName)
          remoteCommitCount != null && remoteCommitCount > BuildConfig.GIT_COUNT
        }
      }

    return if (isNewer) {
      release
    } else {
      null
    }
  }

  fun ignoreVersion(
    version: String,
    channel: AppUpdateChannel,
  ) {
    // No-op if update feature is disabled
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
      return
    }

    val prefs = context.getSharedPreferences("mpvrx_prefs", Context.MODE_PRIVATE)
    prefs
      .edit()
      .putString(ignoredVersionKey(channel), version)
      .apply()
  }

  private suspend fun getLatestRelease(url: String): Release =
    withContext(Dispatchers.IO) {
      val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        val responseBody = response.body.string()
        json.decodeFromString<Release>(responseBody)
      }
    }

  private fun isNewerVersion(
    remote: String,
    current: String,
  ): Boolean {
    val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }

    for (i in 0 until maxOf(rParts.size, cParts.size)) {
      val r = rParts.getOrElse(i) { 0 }
      val c = cParts.getOrElse(i) { 0 }
      if (r > c) return true
      if (r < c) return false
    }
    return false
  }

  private fun parsePreviewCommitCount(tagName: String): Int? =
    PREVIEW_TAG_REGEX.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()

  private fun ignoredVersionKey(channel: AppUpdateChannel): String =
    "ignored_version_${channel.name.lowercase()}"

  fun downloadUpdate(release: Release): Flow<Float> {
    // Return completed flow immediately if update feature is disabled
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
      return flowOf(100f)
    }

    val asset =
      selectBestApkAsset(release.assets)
        ?: throw Exception("No compatible APK asset found")

    val expectedSha256 =
      UpdateIntegrity.expectedSha256(asset, release.body)
        ?: throw IOException("No published SHA-256 for ${asset.name}; refusing to download an unverifiable update")
    val directory = updatesDirectory() ?: throw IOException("External cache is unavailable")
    return downloadApk(asset, expectedSha256, File(directory, asset.name))
  }

  /** Verified APKs live in their own directory, which is the only external-cache path shared via FileProvider. */
  private fun updatesDirectory(): File? =
    context.externalCacheDir?.let { File(it, UPDATES_DIRECTORY) }?.apply { mkdirs() }

  private fun selectBestApkAsset(assets: List<Asset>): Asset? {
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
      pattern = "(?:^|-)${Regex.escape(token)}(?:-|\\.apk$)",
      option = RegexOption.IGNORE_CASE,
    ).containsMatchIn(this)

  private fun getDeviceArchitecture(): String {
    // Get the primary ABI (Application Binary Interface)
    val primaryAbi =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Build.SUPPORTED_ABIS[0]
      } else {
        @Suppress("DEPRECATION")
        Build.CPU_ABI
      }

    // Map Android ABI names to your APK naming convention
    return when (primaryAbi) {
      "arm64-v8a" -> "arm64-v8a"
      "armeabi-v7a" -> "armeabi-v7a"
      "x86" -> "x86"
      "x86_64" -> "x86_64"
      else -> "universal" // Fallback for unknown architectures
    }
  }

  /**
   * Streams into a `.part` file while hashing, then renames it into place only after the size and
   * SHA-256 match the release metadata. This catches interrupted, truncated or corrupted downloads;
   * it is not an authenticity check, because the hash comes from the same feed as the download URL.
   * Authenticity still rests on HTTPS and Android's installer requiring the same signing certificate.
   */
  private fun downloadApk(
    asset: Asset,
    expectedSha256: String,
    destination: File,
  ): Flow<Float> =
    flow {
      val partial = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
      partial.delete()
      destination.delete()
      try {
        val request = Request.Builder().url(asset.downloadUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytesRead = 0L
        var lastPercent = Int.MIN_VALUE
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IOException("Unexpected code $response")
          val body = response.body
          val contentLength = body.contentLength().takeIf { it > 0 } ?: asset.size
          body.byteStream().use { input ->
            FileOutputStream(partial).use { output ->
              val buffer = ByteArray(64 * 1024)
              while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                // Emit only when the whole percentage changes (or once for unknown length).
                val percent = if (contentLength > 0) (totalBytesRead * 100 / contentLength).toInt() else -1
                if (percent != lastPercent) {
                  lastPercent = percent
                  emit(percent.toFloat())
                }
              }
              output.fd.sync()
            }
          }
        }
        if (asset.size > 0 && totalBytesRead != asset.size) {
          throw IOException("Downloaded ${asset.name} is $totalBytesRead bytes, expected ${asset.size}")
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
          throw IOException("SHA-256 mismatch for ${asset.name}")
        }
        if (!partial.renameTo(destination)) throw IOException("Could not finalize ${asset.name}")
        emit(100f)
      } catch (error: Throwable) {
        partial.delete()
        destination.delete()
        throw error
      }
    }.flowOn(Dispatchers.IO)

  /**
   * Returns the cached APK only if it still matches the release's published size and SHA-256.
   * The cache lives in external storage, so it is re-hashed (off the main thread) before every
   * use; a missing hash, wrong size, hashing error or mismatch returns null and drops the file so
   * the caller downloads it again.
   */
  suspend fun getApkFile(release: Release): File? {
    // Return null if update feature is disabled
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
      return null
    }

    val asset = selectBestApkAsset(release.assets) ?: return null
    val expectedSha256 = UpdateIntegrity.expectedSha256(asset, release.body) ?: return null
    return withContext(Dispatchers.IO) {
      val file = File(updatesDirectory() ?: return@withContext null, asset.name)
      if (!file.isFile) return@withContext null
      val valid =
        (asset.size <= 0 || file.length() == asset.size) &&
          runCatching { UpdateIntegrity.sha256Of(file) }.getOrNull() == expectedSha256
      if (valid) file else null.also { file.delete() }
    }
  }

  fun clearCache() {
    // No-op if update feature is disabled
    if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
      return
    }

    updatesDirectory()?.listFiles()?.forEach { it.delete() }
    // Earlier builds downloaded straight into the external cache root.
    context.externalCacheDir?.listFiles()?.forEach {
      if (it.isFile && it.name.endsWith(".apk")) it.delete()
    }
  }

  private companion object {
    const val STABLE_RELEASE_URL = "https://api.github.com/repos/Fahimofficial/QuantumMPV/releases/latest"
    const val PREVIEW_RELEASE_URL = "https://fahimofficial.github.io/QuantumMPV/latest.json"
    const val LEGACY_IGNORED_VERSION_KEY = "ignored_version"
    const val UPDATES_DIRECTORY = "updates"
    const val PARTIAL_SUFFIX = ".part"
    val PREVIEW_TAG_REGEX = Regex("""(?:preview-)?r(\d+)""", RegexOption.IGNORE_CASE)
    val SUPPORTED_ARCHITECTURES = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
  }
}
