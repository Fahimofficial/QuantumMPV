/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.domain.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File

object EmbeddedArtworkCandidates {
  private val artworkExtensions = listOf("jpg", "jpeg", "png", "webp")
  private val genericArtworkNames = listOf("cover", "folder", "poster", "thumbnail")

  fun forVideoPath(path: String): List<String> {
    if (path.isBlank() || path.isRemoteOrOpaqueUri()) return emptyList()
    val normalizedPath = path.replace('\\', '/')
    val parent =
      normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        ?: return emptyList()
    val fileName = normalizedPath.substringAfterLast('/')
    val baseName =
      fileName.substringBeforeLast('.', missingDelimiterValue = fileName).takeIf { it.isNotBlank() }
        ?: return emptyList()

    return buildList {
      artworkExtensions.forEach { extension ->
        add("$parent/$baseName.$extension")
      }
      artworkExtensions.forEach { extension ->
        add("$parent/$baseName.cover.$extension")
        add("$parent/$baseName-cover.$extension")
      }
      genericArtworkNames.forEach { name ->
        artworkExtensions.forEach { extension ->
          add("$parent/$name.$extension")
        }
      }
    }.distinct()
  }

  private fun String.isRemoteOrOpaqueUri(): Boolean =
    startsWith("http://", ignoreCase = true) ||
      startsWith("https://", ignoreCase = true) ||
      startsWith("rtmp://", ignoreCase = true) ||
      startsWith("rtsp://", ignoreCase = true) ||
      startsWith("ftp://", ignoreCase = true) ||
      startsWith("sftp://", ignoreCase = true) ||
      startsWith("smb://", ignoreCase = true) ||
      startsWith("content://", ignoreCase = true)
}

internal object EmbeddedArtworkResolver {
  /**
   * Artwork is shown at most about screen-width; decoding a 3000 px cover at full size costs ~36 MB
   * and churns the shared image cache. Sampling keeps the longest edge at or above this size.
   */
  private const val MAX_ARTWORK_DECODE_SIZE = 1024

  /** Largest remote artwork body buffered before decoding; covers are rarely above a few MB. */
  private const val MAX_REMOTE_ARTWORK_BYTES = 16L * 1024 * 1024

  /**
   * Runs [decode] once for bounds, then again with an [BitmapFactory.Options.inSampleSize]. Returns
   * null when the bounds pass can't read a size, rather than falling back to an unsampled decode.
   */
  private inline fun decodeBounded(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options =
      BitmapFactory.Options().apply {
        inSampleSize = calculateThumbnailSampleSize(bounds.outWidth, bounds.outHeight, MAX_ARTWORK_DECODE_SIZE)
      }
    return decode(options)
  }

  /** Reads at most [MAX_REMOTE_ARTWORK_BYTES], or null if the body is larger. */
  private fun java.io.InputStream.readBoundedBytes(): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
      val read = read(buffer)
      if (read < 0) return out.toByteArray()
      total += read
      if (total > MAX_REMOTE_ARTWORK_BYTES) return null
      out.write(buffer, 0, read)
    }
  }

  fun decodeArtworkUri(
    context: Context,
    artworkUri: String?,
  ): Bitmap? {
    if (artworkUri.isNullOrBlank()) return null
    com.quantummpv.app.presentation.components.RemoteImageLoader.getFromMemory(artworkUri)?.let { return it }
    val uri = Uri.parse(artworkUri)
    return runCatching {
      val decoded =
        when (uri.scheme?.lowercase()) {
          null, "" -> decodeBounded { options -> BitmapFactory.decodeFile(artworkUri, options) }
          "file" -> decodeBounded { options -> BitmapFactory.decodeFile(uri.path, options) }
          "content", "android.resource" ->
            // Opened once for the bounds pass and once for the sampled decode.
            decodeBounded { options ->
              context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
            }
          "http", "https" -> {
            val connection = (java.net.URL(artworkUri).openConnection() as java.net.HttpURLConnection).apply {
              connectTimeout = 8000
              readTimeout = 8000
              instanceFollowRedirects = true
              setRequestProperty("User-Agent", "Mozilla/5.0 (Android) QuantumMPV")
            }
            // Reject an oversized declared length up front; an unknown length is capped while reading.
            val bytes =
              connection.inputStream.use { input ->
                if (connection.contentLengthLong > MAX_REMOTE_ARTWORK_BYTES) null else input.readBoundedBytes()
              }
            bytes?.let { body -> decodeBounded { options -> BitmapFactory.decodeByteArray(body, 0, body.size, options) } }
          }
          else -> null
        }
      decoded?.also {
        com.quantummpv.app.presentation.components.RemoteImageLoader.putInMemory(artworkUri, it)
      }
    }.getOrNull()
  }

  fun decodeEmbeddedArtwork(
    videoPath: String?,
    retriever: MediaMetadataRetriever,
  ): Bitmap? =
    decodeRetrieverArtwork(retriever)
      ?: MatroskaEmbeddedArtworkExtractor.decode(videoPath)
      ?: decodeSidecar(videoPath)

  fun decodeSidecar(videoPath: String?): Bitmap? =
    videoPath
      ?.let(EmbeddedArtworkCandidates::forVideoPath)
      ?.asSequence()
      ?.map(::File)
      ?.firstNotNullOfOrNull { candidate ->
        candidate
          .takeIf { it.isFile && it.canRead() }
          ?.let { file -> decodeBounded { options -> BitmapFactory.decodeFile(file.path, options) } }
      }

  fun decodeRetrieverArtwork(retriever: MediaMetadataRetriever): Bitmap? {
    retriever.embeddedPicture
      ?.takeIf { it.isNotEmpty() }
      ?.let { bytes -> decodeBounded { options -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) } }
      ?.let { return it }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching { retriever.getPrimaryImage() }
        .getOrNull()
        ?.let { return it }
    }

    return null
  }
}
