/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.visualizer

import android.opengl.GLSurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
internal fun BlobOverlay(
  modifier: Modifier = Modifier,
  palette: VisualizerPalette,
  isSheetOpen: Boolean = false,
  features: AudioFeatures,
) = VisualizerOverlay(
  modifier = modifier,
  palette = palette,
  isSheetOpen = isSheetOpen,
  features = features,
  factory = { ctx, features, p -> BlobVisualizerView(ctx, features, p) },
)

@Composable
internal fun GalaxyOverlay(
  modifier: Modifier = Modifier,
  palette: VisualizerPalette,
  isSheetOpen: Boolean = false,
  features: AudioFeatures,
) = VisualizerOverlay(
  modifier = modifier,
  palette = palette,
  isSheetOpen = isSheetOpen,
  features = features,
  factory = { ctx, features, p -> GalaxyVisualizerView(ctx, features, p) },
)

@Composable
internal fun ParticleOverlay(
  modifier: Modifier = Modifier,
  palette: VisualizerPalette,
  isSheetOpen: Boolean = false,
  features: AudioFeatures,
) = VisualizerOverlay(
  modifier = modifier,
  palette = palette,
  isSheetOpen = isSheetOpen,
  features = features,
  factory = { ctx, features, p -> ParticleVisualizerView(ctx, features, p) },
)

internal interface PaletteConsumer {
  fun updatePalette(value: VisualizerPalette)
}

@Composable
private fun <T> VisualizerOverlay(
  modifier: Modifier = Modifier,
  palette: VisualizerPalette,
  isSheetOpen: Boolean = false,
  features: AudioFeatures,
  factory: (android.content.Context, AudioFeatures, VisualizerPalette) -> T,
) where T : GLSurfaceView, T : PaletteConsumer {
  AndroidView(
    factory = { ctx ->
      factory(ctx, features, palette).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    },
    modifier = modifier,
    update = { view ->
      view.updatePalette(palette)
      if (isSheetOpen) {
        // Translucent GL surfaces sit above the app window, so hide them while a sheet is open.
        view.visibility = View.INVISIBLE
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
      } else {
        view.visibility = View.VISIBLE
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
      }
    },
  )
}

/** Lightweight feature state shared by every renderer and the audio-reactive seekbar. */
@Composable
internal fun rememberAudioVisualizerFeatures(
  isPlaying: Boolean,
  volumeScale: Float,
): AudioFeatures {
  val features = remember { AudioFeatures() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(volumeScale) {
    features.volumeScale = volumeScale.coerceIn(0f, 1f)
  }

  DisposableEffect(isPlaying) {
    val job =
      scope.launch(Dispatchers.Default) {
        while (isActive) {
          val captureEpoch = features.lastCaptureNanos
          val realCapture = features.active && features.hasRecentCapture(1_500_000_000L)
          if (!realCapture && isPlaying) {
            val time = System.nanoTime() / 1_000_000_000f
            // A real capture can land while the idle frame is being synthesized. Re-check the
            // capture epoch right before writing so fresh analyzer data is never clobbered.
            if (features.lastCaptureNanos == captureEpoch) {
              features.energy = 0.025f + sin(time * 0.72f) * 0.006f
              features.bass = 0.018f + sin(time * 0.55f) * 0.004f
              features.mid = 0.014f + sin(time * 0.83f) * 0.003f
              features.treble = 0.010f + sin(time * 1.05f) * 0.002f
              features.beat = 0f
              features.centroid = 0.35f
              features.active = false
            }
          } else if (!isPlaying) {
            features.decay(0.90f, beatFactor = 0.75f)
          }
          delay(33)
        }
      }
    onDispose { job.cancel() }
  }
  return features
}
