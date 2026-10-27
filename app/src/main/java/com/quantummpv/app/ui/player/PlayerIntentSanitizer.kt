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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import java.io.File
import java.security.SecureRandom

/**
 * Marks player launches that originate inside this process. The token is random per process, so
 * another app cannot forge it by copying extras; a launch replayed after process death simply
 * loses the elevated trust and is treated like any external intent.
 */
object InternalPlayerLaunch {
  const val EXTRA_TOKEN = "com.quantummpv.app.extra.INTERNAL_LAUNCH_TOKEN"

  private val token: String =
    ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

  /** Adds the in-process launch token to [intent] and returns it for chaining. */
  fun mark(intent: Intent): Intent = intent.putExtra(EXTRA_TOKEN, token)

  fun isInternal(intent: Intent?): Boolean = intent?.getStringExtra(EXTRA_TOKEN) == token
}

/**
 * Pure path and URI classification for [PlayerIntentSanitizer]. Kept free of Android framework
 * lookups so it can be unit tested on the JVM.
 */
class PrivateStoragePolicy(
  private val packageName: String,
  privateRoots: List<File>,
) {
  private val canonicalRoots: List<String> =
    privateRoots.mapNotNull { root -> runCatching { root.canonicalPath }.getOrNull() }.distinct()
  private val privateDataPattern =
    Regex("^/data/(data|user/\\d+|user_de/\\d+)/${Regex.escape(packageName)}(/.*)?$")

  /** True when [path] resolves (after `..` and symlink normalisation) into this app's private storage. */
  fun isPrivatePath(path: String): Boolean {
    if (!path.startsWith("/")) return false
    val canonical = runCatching { File(path).canonicalPath }.getOrDefault(File(path).absolutePath)
    return privateDataPattern.matches(canonical) ||
      canonicalRoots.any { root -> canonical == root || canonical.startsWith("$root/") }
  }

  /**
   * True when [uri] points into private storage or at one of this app's own content providers.
   * External callers have no reason to hand the player our own provider URIs; doing so would let
   * them read files that the non-exported provider exposes only to this process.
   */
  fun isPrivateUri(uri: Uri): Boolean =
    when (val scheme = uri.scheme?.lowercase()) {
      null, "" -> uri.path?.let(::isPrivatePath) == true
      "file" -> uri.path?.let(::isPrivatePath) == true
      "content" -> uri.authority?.let { it == packageName || it.startsWith("$packageName.") } == true
      // mpv/ffmpeg pseudo-protocols (edl://, av://file:, lavf://, slice://, fd://, archive://…)
      // can wrap a local path, and the app's own schemes resolve saved server credentials. Other
      // apps only get the plain network and file schemes a player is expected to open.
      else -> scheme !in EXTERNAL_SCHEMES
    }

  fun isPrivateReference(value: String): Boolean =
    if (value.startsWith("/")) isPrivatePath(value) else isPrivateUri(Uri.parse(value.trim()))

  companion object {
    /** Schemes other apps may ask the player to open, besides file and content. */
    val EXTERNAL_SCHEMES =
      setOf(
        "http",
        "https",
        "rtsp",
        "rtsps",
        "rtmp",
        "rtmps",
        "rtp",
        "srt",
        "mms",
        "mmsh",
        "mmst",
        "udp",
        "tcp",
        "ftp",
        "ftps",
        "data",
        "magnet",
        "torrent",
      )
  }
}

/**
 * Strips references to this app's private storage from player intents sent by other apps. The
 * player is exported and holds broad storage access, so without this any app could ask it to open
 * files that only QuantumMPV can read, such as media moved into the secure folder.
 */
object PlayerIntentSanitizer {
  private const val TAG = "PlayerIntentSanitizer"

  private val pathExtras = listOf("local_media_path", "uri")
  private val uriListExtras = listOf("playlist", Intent.EXTRA_STREAM, "subs", "subs.enable")
  private val stringListExtras = listOf("video_list")

  /**
   * Extras that make the player act on data only the app should be able to name: saved network
   * connections (and their stored credentials), saved playlists, prepared torrents and playback
   * state keys. In-process launches carry [InternalPlayerLaunch]; other apps lose these extras.
   */
  private val internalOnlyExtras =
    listOf(
      "network_connection_id",
      "network_file_path",
      "network_playlist_connection_id",
      "network_playlist_paths",
      "network_playlist_titles",
      "network_playlist_artwork_urls",
      "playlist_id",
      "torrent_preparation_id",
      "media_identifier",
    )

  fun policyFor(context: Context): PrivateStoragePolicy {
    val roots =
      buildList {
        add(File(context.applicationInfo.dataDir))
        add(context.filesDir)
        add(context.noBackupFilesDir)
        add(context.cacheDir)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          add(File(context.createDeviceProtectedStorageContext().applicationInfo.dataDir))
        }
      }
    return PrivateStoragePolicy(context.packageName, roots)
  }

  /** Returns [intent] unchanged for in-process launches; otherwise removes private references. */
  fun sanitize(
    context: Context,
    intent: Intent,
  ): Intent {
    if (InternalPlayerLaunch.isInternal(intent)) return intent
    val policy = policyFor(context)
    var removed = 0

    intent.data?.let { data ->
      if (policy.isPrivateUri(data)) {
        intent.setDataAndType(null, intent.type)
        removed++
      }
    }

    val extras = intent.extras ?: return intent.also { logRemoved(removed) }

    internalOnlyExtras.forEach { key ->
      if (extras.containsKey(key)) {
        intent.removeExtra(key)
        removed++
      }
    }

    pathExtras.forEach { key ->
      val value = runCatching { extras.getString(key) }.getOrNull() ?: return@forEach
      if (policy.isPrivateReference(value)) {
        intent.removeExtra(key)
        removed++
      }
    }

    uriListExtras.forEach { key ->
      if (!extras.containsKey(key)) return@forEach
      removed += sanitizeUriExtra(intent, extras, key, policy)
    }

    // Shared text can carry a URL that SharedUrlExtractor later turns into the media source.
    runCatching { extras.getString(Intent.EXTRA_TEXT) }.getOrNull()?.let { text ->
      val mentionsPrivateStorage =
        text
          .split(Regex("\\s+"))
          .map { it.trim('"', '\'', '<', '>', '(', ')', ',') }
          .any { token ->
            (token.startsWith("/") || token.contains("://")) && policy.isPrivateReference(token)
          }
      if (mentionsPrivateStorage) {
        intent.removeExtra(Intent.EXTRA_TEXT)
        removed++
      }
    }

    stringListExtras.forEach { key ->
      val values = runCatching { extras.getStringArrayList(key) }.getOrNull() ?: return@forEach
      val kept = values.filterNot(policy::isPrivateReference)
      if (kept.size != values.size) {
        intent.putStringArrayListExtra(key, ArrayList(kept))
        removed += values.size - kept.size
      }
    }

    logRemoved(removed)
    return intent
  }

  /** Filters one extra that may be stored as a Uri, Uri list/array, or String list/array. */
  private fun sanitizeUriExtra(
    intent: Intent,
    extras: Bundle,
    key: String,
    policy: PrivateStoragePolicy,
  ): Int {
    @Suppress("DEPRECATION")
    val raw = runCatching { extras.get(key) }.getOrNull() ?: return 0
    return when (raw) {
      is Uri ->
        if (policy.isPrivateUri(raw)) {
          intent.removeExtra(key)
          1
        } else {
          0
        }
      is String ->
        if (policy.isPrivateReference(raw)) {
          intent.removeExtra(key)
          1
        } else {
          0
        }
      is Array<*> -> {
        val removed =
          raw.count { item ->
            (item as? Uri)?.let(policy::isPrivateUri) == true ||
              (item as? String)?.let(policy::isPrivateReference) == true
          }
        if (removed > 0) {
          if (raw.isArrayOf<String>()) {
            intent.putExtra(key, raw.filterIsInstance<String>().filterNot(policy::isPrivateReference).toTypedArray())
          } else {
            intent.putExtra(key, raw.filterIsInstance<Uri>().filterNot(policy::isPrivateUri).toTypedArray<Parcelable>())
          }
        }
        removed
      }
      is ArrayList<*> -> {
        val keptUris = raw.filterIsInstance<Uri>().filterNot(policy::isPrivateUri)
        val keptStrings = raw.filterIsInstance<String>().filterNot(policy::isPrivateReference)
        val removed = raw.size - keptUris.size - keptStrings.size
        if (removed > 0) {
          if (keptStrings.isNotEmpty() || raw.firstOrNull() is String) {
            intent.putStringArrayListExtra(key, ArrayList(keptStrings))
          } else {
            intent.putParcelableArrayListExtra(key, ArrayList(keptUris))
          }
        }
        removed
      }
      else -> 0
    }
  }

  private fun logRemoved(removed: Int) {
    if (removed > 0) Log.w(TAG, "Ignored $removed private-storage reference(s) from an external player intent")
  }
}
