package com.quantummpv.app.ui.player.visualizer

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BlobOverlay(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val features = remember { AudioFeatures() }
    val scope = rememberCoroutineScope()

    // Try real FFT capture via Visualizer API
    DisposableEffect(Unit) {
        val hasRecordPermission =
            Build.VERSION.SDK_INT < 23 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val analyzer = if (hasRecordPermission) {
            AudioSpectrumAnalyzer(features).also { it.start(0) }
        } else null

        onDispose {
            analyzer?.stop()
        }
    }

    // Simulated audio animation (active even without real FFT)
    DisposableEffect(isPlaying) {
        val job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (isPlaying) {
                    val time = System.nanoTime() / 1_000_000_000f
                    val noise = Random.nextFloat() * 0.3f + 0.1f
                    features.energy = (sin(time * 2.3f) * 0.15f + 0.25f + noise * 0.3f).coerceIn(0f, 1f)
                    features.bass = (sin(time * 1.7f) * 0.12f + 0.12f + noise * 0.25f).coerceIn(0f, 1f)
                    features.mid = (sin(time * 3.1f) * 0.10f + 0.15f + noise * 0.2f).coerceIn(0f, 1f)
                    features.treble = (sin(time * 5.7f) * 0.08f + 0.10f + noise * 0.15f).coerceIn(0f, 1f)
                    features.beat = (if (sin(time * 1.1f) > 0.7f) 1f else features.beat * 0.76f)
                    features.centroid = (sin(time * 0.8f) * 0.2f + 0.35f).coerceIn(0f, 1f)
                    features.active = true
                } else {
                    features.bass *= 0.95f
                    features.mid *= 0.93f
                    features.treble *= 0.91f
                    features.energy *= 0.94f
                    features.beat *= 0.85f
                }
                delay(33)
            }
        }

        onDispose {
            job.cancel()
        }
    }

    AndroidView(
        factory = { ctx ->
            BlobVisualizerView(ctx, features).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier,
        update = { view ->
            view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        },
    )
}
