/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.utils.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.LruCache
import com.quantummpv.app.domain.media.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val VIDEO_CODEC_LABELS = mapOf(
  MediaFormat.MIMETYPE_VIDEO_HEVC to "HEVC",
  MediaFormat.MIMETYPE_VIDEO_AVC to "H.264",
  MediaFormat.MIMETYPE_VIDEO_AV1 to "AV1",
  MediaFormat.MIMETYPE_VIDEO_VP9 to "VP9",
  MediaFormat.MIMETYPE_VIDEO_VP8 to "VP8",
  MediaFormat.MIMETYPE_VIDEO_MPEG4 to "MPEG-4",
  MediaFormat.MIMETYPE_VIDEO_MPEG2 to "MPEG-2",
  MediaFormat.MIMETYPE_VIDEO_H263 to "H.263",
  "video/dolby-vision" to "Dolby Vision",
)

/** Short codec name for a video track MIME type ("video/hevc" -> "HEVC"), null when not a video MIME. */
fun codecDisplayName(mime: String?): String? {
  if (mime == null || !mime.startsWith("video/")) return null
  val normalized = mime.lowercase()
  return VIDEO_CODEC_LABELS[normalized]
    ?: normalized.substringAfter('/').substringAfterLast('.').uppercase().takeIf { it.isNotBlank() }
}

/**
 * Codec labels for list chips. Reads only the container header with MediaExtractor (no decoding), on
 * IO, two probes at a time, and caches by file identity so a row scrolled back in is instant.
 */
object VideoCodecLabelCache {
  private val cache = LruCache<String, String>(1024)
  private val probes = Semaphore(2)

  private fun key(video: Video) = "${video.path}|${video.size}|${video.dateModified}"

  /** Cached label without probing: "" when known to be unavailable, null when not probed yet. */
  fun peek(video: Video): String? = cache.get(key(video))

  suspend fun load(
    context: Context,
    video: Video,
  ): String? {
    val key = key(video)
    cache.get(key)?.let { return it.ifEmpty { null } }
    val label =
      probes.withPermit {
        withContext(Dispatchers.IO) {
          // "" marks files without a readable video track so they are not probed again.
          cache.get(key) ?: probe(context, video).orEmpty().also { cache.put(key, it) }
        }
      }
    return label.ifEmpty { null }
  }

  private fun probe(
    context: Context,
    video: Video,
  ): String? {
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(context, video.uri, null)
      (0 until extractor.trackCount)
        .firstNotNullOfOrNull { codecDisplayName(extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)) }
    } catch (_: Exception) {
      null
    } finally {
      extractor.release()
    }
  }
}
