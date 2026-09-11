/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.domain.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build

/**
 * Conservative capability inspection for Media3 routing.
 *
 * This intentionally reports codec-family availability rather than promising that every profile,
 * level, transfer function, or device compositor path is fully supported. Final playback support
 * is still determined by Media3 at runtime.
 */
object Media3Capability {
  data class Snapshot(
    val apiLevel: Int,
    val hasDolbyVisionDecoder: Boolean,
    val hasHdr10PlusDecoder: Boolean,
    val hasHevcDecoder: Boolean,
    val hasAv1Decoder: Boolean,
  )

  fun inspect(context: Context): Snapshot {
    // Touch the application context so callers can safely pass an Activity without retaining it.
    context.applicationContext

    val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    var dolbyVision = false
    var hdr10Plus = false
    var hevc = false
    var av1 = false

    codecList.codecInfos.forEach { codecInfo ->
      if (codecInfo.isEncoder) return@forEach
      codecInfo.supportedTypes.forEach { type ->
        val normalized = type.lowercase()
        when {
          normalized == "video/dolby-vision" -> dolbyVision = true
          normalized == "video/hevc" -> hevc = true
          normalized == "video/av01" -> av1 = true
        }
      }

      // HDR10+ is exposed inconsistently across vendors. Codec presence alone must not be
      // interpreted as full HDR10+ rendering support, so this remains conservative for now.
      if (Build.VERSION.SDK_INT >= 24) {
        val capabilities = runCatching { codecInfo.getCapabilitiesForType("video/hevc") }.getOrNull()
        if (capabilities?.videoCapabilities != null) {
          hdr10Plus = hdr10Plus || false
        }
      }
    }

    return Snapshot(
      apiLevel = Build.VERSION.SDK_INT,
      hasDolbyVisionDecoder = dolbyVision,
      hasHdr10PlusDecoder = hdr10Plus,
      hasHevcDecoder = hevc,
      hasAv1Decoder = av1,
    )
  }
}
