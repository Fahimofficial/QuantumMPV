/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.quantummpv.app.ui.player

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/** Installs an app-owned Unicode fallback font where libmpv/fontconfig searches for fonts. */
internal object SubtitleFontInstaller {
  internal const val ASSET_PATH = "subtitle-fonts/GoNotoCurrent-Regular.ttf"
  internal const val FONT_FILE_NAME = "QuantumMPV-GoNotoCurrent-Regular-2.012.ttf"
  internal const val FONT_SIZE_BYTES = 14_700_060L
  internal const val FONT_SHA256 = "882afbab965608c2d2bc627fd8016b962aa5a6be2d358f9de24a7b5967c5632e"

  private const val TAG = "SubtitleFontInstaller"

  /**
   * Installs the fallback in the app's default fonts directory before libmpv initializes. A failed
   * copy is non-fatal: mpv can still use Android/fontconfig-provided system fonts.
   */
  @Synchronized
  fun install(context: Context): File? {
    val appContext = context.applicationContext
    return try {
      installFromSource(
        directory = File(appContext.filesDir, "fonts"),
        fileName = FONT_FILE_NAME,
        expectedSizeBytes = FONT_SIZE_BYTES,
        expectedSha256 = FONT_SHA256,
      ) { appContext.assets.open(ASSET_PATH) }
    } catch (error: Exception) {
      Log.w(TAG, "Could not install the bundled subtitle fallback font; using system fonts.", error)
      null
    }
  }

  /**
   * Adds the bundled font to mpv's effective custom font directory without changing that option.
   * libmpv resolves options from mpv.conf during init, so this is called from postInitOptions before
   * any media or subtitle track is loaded. The app's default directory is already populated by
   * [install] and does not need to be written twice.
   */
  @Synchronized
  internal fun installInConfiguredDirectory(
    context: Context,
    configDirectoryPath: String,
    configuredDirectory: String?,
    fileName: String = FONT_FILE_NAME,
    expectedSizeBytes: Long = FONT_SIZE_BYTES,
    expectedSha256: String = FONT_SHA256,
    openSource: (() -> InputStream)? = null,
  ): File? {
    val appContext = context.applicationContext
    val configDirectory = File(configDirectoryPath)
    val directory = resolveConfiguredDirectory(configDirectory, configuredDirectory) ?: return null
    val defaultDirectory = File(appContext.filesDir, "fonts")
    if (sameDirectory(directory, defaultDirectory)) return null

    return try {
      installFromSource(
        directory = directory,
        fileName = fileName,
        expectedSizeBytes = expectedSizeBytes,
        expectedSha256 = expectedSha256,
        openSource = openSource ?: { appContext.assets.open(ASSET_PATH) },
      )
    } catch (error: Exception) {
      Log.w(TAG, "Could not install the bundled subtitle font in mpv's configured directory: $directory", error)
      null
    }
  }

  /** Resolve mpv's config-relative (~~/) and home-relative (~/) path forms for filesystem access. */
  internal fun resolveConfiguredDirectory(
    configDirectory: File,
    configuredDirectory: String?,
  ): File? {
    val value = configuredDirectory?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val path = value.removeSurrounding("\"").removeSurrounding("'")
    val homeDirectory = configDirectory.parentFile ?: configDirectory
    return when {
      path == "~~" -> configDirectory
      path.startsWith("~~/") -> File(configDirectory, path.removePrefix("~~/"))
      path == "~" -> homeDirectory
      path.startsWith("~/") -> File(homeDirectory, path.removePrefix("~/"))
      File(path).isAbsolute -> File(path)
      else -> File(configDirectory, path)
    }
  }

  /** The core copy routine accepts a source factory so it can be tested without Robolectric assets. */
  @Synchronized
  internal fun installFromSource(
    directory: File,
    fileName: String,
    expectedSizeBytes: Long,
    expectedSha256: String,
    openSource: () -> InputStream,
  ): File {
    check(directory.isDirectory || directory.mkdirs()) {
      "Unable to create subtitle fonts directory: $directory"
    }

    val installedFont = File(directory, fileName)
    if (
      installedFont.isFile &&
      installedFont.length() == expectedSizeBytes &&
      sha256(installedFont) == expectedSha256
    ) {
      return installedFont
    }

    val stagedFont = File(directory, ".$fileName.tmp")
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      openSource().use { asset ->
        DigestInputStream(asset, digest).use { input ->
          FileOutputStream(stagedFont).use { output -> input.copyTo(output) }
        }
      }

      check(stagedFont.length() == expectedSizeBytes) { "Bundled subtitle font has an unexpected size." }
      check(digest.digest().toHex() == expectedSha256) { "Bundled subtitle font checksum mismatch." }
      if (installedFont.exists()) {
        check(installedFont.delete()) { "Unable to replace stale subtitle font: $installedFont" }
      }
      check(stagedFont.renameTo(installedFont)) { "Unable to install subtitle font: $installedFont" }
      installedFont
    } catch (error: Exception) {
      stagedFont.delete()
      throw error
    }
  }

  private fun sameDirectory(
    first: File,
    second: File,
  ): Boolean =
    runCatching { first.canonicalFile == second.canonicalFile }
      .getOrDefault(first.absoluteFile == second.absoluteFile)

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val bytesRead = input.read(buffer)
        if (bytesRead < 0) break
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().toHex()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
