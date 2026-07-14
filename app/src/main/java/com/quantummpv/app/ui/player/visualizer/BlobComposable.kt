package com.quantummpv.app.ui.player.visualizer

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

@Composable
fun BlobOverlay(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val features = remember { AudioFeatures() }
    val scope = rememberCoroutineScope()
    val realAnalyzerActive = remember { AtomicBoolean(false) }
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val recordPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRecordPermission = granted
        }

    LaunchedEffect(hasRecordPermission) {
        if (!hasRecordPermission) recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Try real FFT capture via Visualizer API
    DisposableEffect(hasRecordPermission) {
        val analyzer = if (hasRecordPermission) AudioSpectrumAnalyzer(features) else null
        realAnalyzerActive.set(analyzer?.start(0)?.isSuccess == true)

        onDispose {
            realAnalyzerActive.set(false)
            analyzer?.stop()
        }
    }

    // Keep a restrained deterministic idle motion when system FFT capture is unavailable.
    DisposableEffect(isPlaying) {
        val job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (realAnalyzerActive.get()) {
                    delay(33)
                    continue
                } else if (isPlaying) {
                    val time = System.nanoTime() / 1_000_000_000f
                    features.energy = 0.025f + sin(time * 0.72f) * 0.006f
                    features.bass = 0.018f + sin(time * 0.55f) * 0.004f
                    features.mid = 0.014f + sin(time * 0.83f) * 0.003f
                    features.treble = 0.010f + sin(time * 1.05f) * 0.002f
                    features.beat = 0f
                    features.centroid = 0.35f
                    features.active = false
                } else {
                    features.bass *= 0.90f
                    features.mid *= 0.90f
                    features.treble *= 0.90f
                    features.energy *= 0.90f
                    features.beat *= 0.75f
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
