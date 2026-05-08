package com.quantummpv.app.domain.thumbnail

import com.quantummpv.app.preferences.ThumbnailMode
import kotlin.math.roundToInt

fun ThumbnailMode.toThumbnailStrategy(framePositionPercent: Float): ThumbnailStrategy =
  when (this) {
    ThumbnailMode.Smart -> ThumbnailStrategy.EmbeddedOrHybrid(0.33f)
    ThumbnailMode.FirstFrame -> ThumbnailStrategy.FirstFrame
    ThumbnailMode.FrameAtPosition ->
      ThumbnailStrategy.FrameAtPercentage((framePositionPercent / 100f).coerceIn(0f, 1f))
    ThumbnailMode.EmbeddedThumbnail -> ThumbnailStrategy.EmbeddedOrFirstFrame
  }

internal fun ThumbnailStrategy.prefersEmbeddedPicture(): Boolean =
  this is ThumbnailStrategy.EmbeddedOrFirstFrame || this is ThumbnailStrategy.EmbeddedOrHybrid

fun ThumbnailMode.thumbnailModeCacheKey(framePositionPercent: Float): String =
  when (this) {
    ThumbnailMode.Smart -> "Smart_embedded_v2"
    ThumbnailMode.FrameAtPosition ->
      "FrameAtPosition_${framePositionPercent.coerceIn(0f, 100f).roundToInt()}"
    ThumbnailMode.EmbeddedThumbnail -> "EmbeddedThumbnail_v2"
    else -> name
  }

