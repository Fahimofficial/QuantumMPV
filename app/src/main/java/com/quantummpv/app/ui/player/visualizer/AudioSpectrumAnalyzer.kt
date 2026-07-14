package com.quantummpv.app.ui.player.visualizer

import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class AudioSpectrumAnalyzer(
    val features: AudioFeatures = AudioFeatures()
) {
    private var visualizer: Visualizer? = null

    private var smoothEnergy = 0f
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var smoothCentroid = 0.35f
    private var energyFloor = 0.04f
    private var beatEnvelope = 0f
    private var previousBass = 0f
    private var beatCooldownFrames = 0

    @Synchronized
    fun start(audioSessionId: Int): Result<Unit> {
        stop()
        var pendingInstance: Visualizer? = null
        return runCatching {
            val instance = Visualizer(audioSessionId)
            pendingInstance = instance
            val captureRange = Visualizer.getCaptureSizeRange()
            instance.captureSize = min(captureRange[1], 1024)
            instance.scalingMode = Visualizer.SCALING_MODE_NORMALIZED

            val captureRate = min(Visualizer.getMaxCaptureRate(), 30_000)
            val status = instance.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft != null && fft.size >= 8) {
                            processFft(fft, samplingRate / 1000f)
                        }
                    }
                },
                captureRate,
                false,
                true
            )
            check(status == Visualizer.SUCCESS) { "Visualizer callback setup failed: $status" }
            instance.enabled = true
            visualizer = instance
            pendingInstance = null
            features.active = true
        }.onFailure {
            features.reset()
            runCatching { pendingInstance?.release() }
            runCatching { visualizer?.release() }
            visualizer = null
        }
    }

    @Synchronized
    fun stop() {
        visualizer?.let { instance ->
            runCatching { instance.enabled = false }
            runCatching { instance.setDataCaptureListener(null, 0, false, false) }
            runCatching { instance.release() }
        }
        visualizer = null
        features.reset()
        smoothEnergy = 0f
        smoothBass = 0f
        smoothMid = 0f
        smoothTreble = 0f
        beatEnvelope = 0f
        previousBass = 0f
        beatCooldownFrames = 0
        energyFloor = 0.04f
    }

    private fun processFft(fft: ByteArray, sampleRateHz: Float) {
        val captureSize = fft.size
        val binHz = sampleRateHz / captureSize.toFloat()
        val maxBin = captureSize / 2

        var bassSum = 0f
        var midSum = 0f
        var trebleSum = 0f
        var bassCount = 0
        var midCount = 0
        var trebleCount = 0
        var weightedFrequency = 0f
        var magnitudeSum = 0f

        var k = 1
        while (k < maxBin) {
            val realIndex = k * 2
            val imagIndex = realIndex + 1
            if (imagIndex >= captureSize) break

            val real = fft[realIndex].toInt().toFloat()
            val imaginary = fft[imagIndex].toInt().toFloat()
            val rawMagnitude = hypot(real, imaginary) / 128f
            val magnitude = ln(1f + rawMagnitude * 8f) / ln(9f)
            val frequency = k * binHz

            when {
                frequency < 250f -> {
                    bassSum += magnitude
                    bassCount++
                }
                frequency < 4_000f -> {
                    midSum += magnitude
                    midCount++
                }
                frequency < 16_000f -> {
                    trebleSum += magnitude
                    trebleCount++
                }
            }

            weightedFrequency += magnitude * frequency
            magnitudeSum += magnitude
            k++
        }

        val bass = normalizeBand(bassSum, bassCount, 1.55f)
        val mid = normalizeBand(midSum, midCount, 1.85f)
        val treble = normalizeBand(trebleSum, trebleCount, 2.2f)
        val energy = clamp01(bass * 0.50f + mid * 0.34f + treble * 0.16f)
        val centroidHz = if (magnitudeSum > 0.0001f) weightedFrequency / magnitudeSum else 1_000f
        val centroid = clamp01((centroidHz - 120f) / 9_000f)

        smoothBass = envelope(smoothBass, bass, 0.52f, 0.10f)
        smoothMid = envelope(smoothMid, mid, 0.42f, 0.09f)
        smoothTreble = envelope(smoothTreble, treble, 0.38f, 0.08f)
        smoothEnergy = envelope(smoothEnergy, energy, 0.48f, 0.08f)
        smoothCentroid += (centroid - smoothCentroid) * 0.08f

        energyFloor += (smoothEnergy - energyFloor) * 0.025f
        if (beatCooldownFrames > 0) beatCooldownFrames--
        val bassRise = smoothBass - previousBass
        val beatDetected =
            beatCooldownFrames == 0 &&
                smoothBass > max(0.16f, energyFloor * 1.35f) &&
                bassRise > max(0.028f, energyFloor * 0.16f) &&
                smoothBass > smoothMid * 0.82f
        if (beatDetected) beatCooldownFrames = 5
        beatEnvelope = if (beatDetected) 1f else beatEnvelope * 0.68f
        previousBass = smoothBass

        features.bass = smoothBass
        features.mid = smoothMid
        features.treble = smoothTreble
        features.energy = smoothEnergy
        features.centroid = smoothCentroid
        features.beat = beatEnvelope
        features.active = true
    }

    private fun normalizeBand(sum: Float, count: Int, gain: Float): Float {
        if (count <= 0) return 0f
        return clamp01((sum / count.toFloat()) * gain)
    }

    private fun envelope(current: Float, target: Float, attack: Float, release: Float): Float {
        val amount = if (target > current) attack else release
        return current + (target - current) * amount
    }

    private fun clamp01(value: Float): Float = min(1f, max(0f, value))
}
