/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.domain.update

/** Resolves the SHA-256 an update APK must match before it may be installed. */
internal object UpdateIntegrity {
  private val hexSha256 = Regex("^[0-9a-fA-F]{64}$")

  /**
   * Prefers the hash carried by the asset itself (GitHub `digest` or the preview feed's `sha256`)
   * and falls back to the `sha256sum` block that the release workflows append to the notes.
   */
  fun expectedSha256(
    asset: Asset,
    releaseBody: String,
  ): String? {
    asset.sha256?.trim()?.takeIf(hexSha256::matches)?.let { return it.lowercase() }
    asset.digest
      ?.trim()
      ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
      ?.substringAfter(':')
      ?.takeIf(hexSha256::matches)
      ?.let { return it.lowercase() }
    return checksumFromNotes(releaseBody, asset.name)
  }

  /** Parses `<hash>  <file>` lines, accepting `*` binary markers and path prefixes on the file name. */
  fun checksumFromNotes(
    body: String,
    fileName: String,
  ): String? =
    body.lineSequence().firstNotNullOfOrNull { line ->
      val parts = line.trim().split(Regex("\\s+"), limit = 2)
      if (parts.size != 2 || !hexSha256.matches(parts[0])) return@firstNotNullOfOrNull null
      val name = parts[1].trim().removePrefix("*").substringAfterLast('/')
      parts[0].lowercase().takeIf { name == fileName }
    }
}
