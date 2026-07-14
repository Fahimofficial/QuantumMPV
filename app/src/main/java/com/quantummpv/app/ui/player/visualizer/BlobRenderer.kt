package com.quantummpv.app.ui.player.visualizer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class BlobRenderer(
    private val context: Context,
    private val audio: AudioFeatures
) : GLSurfaceView.Renderer {

    private var blobProgram = 0
    private var brightProgram = 0
    private var blurProgram = 0
    private var compositeProgram = 0

    @Suppress("LocalVariableName")
    private var uBlobMvp = -1
    @Suppress("LocalVariableName")
    private var uBlobTime = -1
    @Suppress("LocalVariableName")
    private var uBlobAudio = -1
    @Suppress("LocalVariableName")
    private var uBlobBass = -1
    @Suppress("LocalVariableName")
    private var uBlobMid = -1
    @Suppress("LocalVariableName")
    private var uBlobTreble = -1
    @Suppress("LocalVariableName")
    private var uBlobBeat = -1
    @Suppress("LocalVariableName")
    private var uBlobColor = -1
    @Suppress("LocalVariableName")
    private var uBlobIntensity = -1
    @Suppress("LocalVariableName")
    private var uBrightScene = -1
    @Suppress("LocalVariableName")
    private var uBrightThreshold = -1
    @Suppress("LocalVariableName")
    private var uBlurImage = -1
    @Suppress("LocalVariableName")
    private var uBlurDirection = -1
    @Suppress("LocalVariableName")
    private var uCompositeScene = -1
    @Suppress("LocalVariableName")
    private var uCompositeBloom = -1
    @Suppress("LocalVariableName")
    private var uCompositeBloomStrength = -1
    @Suppress("LocalVariableName")
    private var uCompositeExposure = -1

    private var meshVao = 0
    private var meshVbo = 0
    private var meshEbo = 0
    private var lineIndexCount = 0

    private var quadVao = 0
    private var quadVbo = 0

    private var sceneTarget = RenderTarget.EMPTY
    private var bloomA = RenderTarget.EMPTY
    private var bloomB = RenderTarget.EMPTY

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var renderScale = 0.82f
    private var targetsDirty = true

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private val rgb = FloatArray(3) { 1f }
    private var smoothR = 0.75f
    private var smoothG = 0.25f
    private var smoothB = 1.0f

    @Volatile private var targetYaw = 0f
    @Volatile private var targetPitch = 0f
    private var yaw = 0f
    private var pitch = 0f

    @Volatile private var pinchScale = 1f
    private var zoomDistance = 8.6f

    fun setPinchScale(scale: Float) {
        pinchScale = scale.coerceIn(0.60f, 1.15f)
    }

    fun getPinchScale(): Float = pinchScale

    private var startNanos = 0L
    private var previousFrameNanos = 0L
    private var frameTimeEmaMs = 16.6f
    private var qualityFrameCounter = 0

    fun addTouchRotation(normalizedDx: Float, normalizedDy: Float) {
        targetYaw += normalizedDx * 150f
        targetPitch = (targetPitch + normalizedDy * 100f).coerceIn(-38f, 38f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        blobProgram = GlUtils.createProgram(
            GlUtils.readAssetText(context, "shaders/visualizer/blob_vertex.glsl"),
            GlUtils.readAssetText(context, "shaders/visualizer/blob_fragment.glsl")
        )
        brightProgram = GlUtils.createProgram(
            GlUtils.readAssetText(context, "shaders/visualizer/quad_vertex.glsl"),
            GlUtils.readAssetText(context, "shaders/visualizer/bright_fragment.glsl")
        )
        blurProgram = GlUtils.createProgram(
            GlUtils.readAssetText(context, "shaders/visualizer/quad_vertex.glsl"),
            GlUtils.readAssetText(context, "shaders/visualizer/blur_fragment.glsl")
        )
        compositeProgram = GlUtils.createProgram(
            GlUtils.readAssetText(context, "shaders/visualizer/quad_vertex.glsl"),
            GlUtils.readAssetText(context, "shaders/visualizer/composite_fragment.glsl")
        )

        cacheUniformLocations()
        createMesh()
        createQuad()
        startNanos = System.nanoTime()
        previousFrameNanos = startNanos
        targetsDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        renderScale = when {
            width.toLong() * height.toLong() >= 3_000_000L -> 0.68f
            width.toLong() * height.toLong() >= 2_000_000L -> 0.76f
            else -> 0.86f
        }
        targetsDirty = true

        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.perspectiveM(projection, 0, 45f, aspect, 0.1f, 30f)
        Matrix.setLookAtM(view, 0, 0f, -0.18f, zoomDistance, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val time = (now - startNanos) / 1_000_000_000f
        val frameMs = (now - previousFrameNanos) / 1_000_000f
        previousFrameNanos = now
        frameTimeEmaMs += (frameMs.coerceAtMost(50f) - frameTimeEmaMs) * 0.025f

        if (targetsDirty) recreateTargets()
        updateAdaptiveQuality()
        updateMatrices(time)
        updateColor(time)

        renderBlob(time)
        extractBrightPass()
        blurBloom(horizontal = true)
        blurBloom(horizontal = false)
        compositeToScreen()
    }

    private fun createMesh() {
        val mesh = Icosphere.create(subdivisions = 5)
        val vertices: FloatBuffer = GlUtils.floatBuffer(mesh.positions)
        val indices: IntBuffer = GlUtils.intBuffer(mesh.lineIndices)
        lineIndexCount = mesh.lineIndices.size

        val vaos = IntArray(1)
        val buffers = IntArray(2)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(2, buffers, 0)
        meshVao = vaos[0]
        meshVbo = buffers[0]
        meshEbo = buffers[1]

        GLES30.glBindVertexArray(meshVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, meshVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            mesh.positions.size * Float.SIZE_BYTES,
            vertices,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, meshEbo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            mesh.lineIndices.size * Int.SIZE_BYTES,
            indices,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
    }

    private fun createQuad() {
        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
        val buffer = GlUtils.floatBuffer(vertices)
        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        quadVao = vaos[0]
        quadVbo = vbos[0]

        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * Float.SIZE_BYTES,
            buffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            2,
            GLES30.GL_FLOAT,
            false,
            4 * Float.SIZE_BYTES,
            2 * Float.SIZE_BYTES
        )
        GLES30.glBindVertexArray(0)
    }

    @Suppress("LocalVariableName")
    private fun updateMatrices(time: Float) {
        yaw += (targetYaw - yaw) * 0.065f
        pitch += (targetPitch - pitch) * 0.065f

        Matrix.setIdentityM(model, 0)
        val idleRotation = time * (4.5f + audio.mid * 5f)
        Matrix.rotateM(model, 0, yaw + idleRotation, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, pitch + time * 1.7f, 1f, 0f, 0f)
        val scale = pinchScale * (0.88f + audio.bass * 0.035f + audio.beat * 0.025f)
        Matrix.scaleM(model, 0, scale, scale, scale)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
    }

    private fun updateColor(time: Float) {
        val reactiveHue = fract(0.73f + audio.centroid * 0.54f + time * 0.018f + audio.beat * 0.08f)
        val saturation = (0.52f + audio.treble * 0.16f).coerceIn(0f, 1f)
        val value = (0.52f + audio.energy * 0.12f).coerceIn(0f, 1f)
        hsvToRgb(reactiveHue, saturation, value, rgb)

        val colorSpeed = 0.035f + audio.energy * 0.06f + audio.beat * 0.10f
        smoothR += (rgb[0] - smoothR) * colorSpeed
        smoothG += (rgb[1] - smoothG) * colorSpeed
        smoothB += (rgb[2] - smoothB) * colorSpeed
    }

    @Suppress("LocalVariableName")
    private fun renderBlob(time: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneTarget.fbo)
        GLES30.glViewport(0, 0, sceneTarget.width, sceneTarget.height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        GLES30.glUseProgram(blobProgram)
        GLES30.glUniformMatrix4fv(uBlobMvp, 1, false, mvp, 0)
        GLES30.glUniform1f(uBlobTime, time)
        GLES30.glUniform1f(uBlobAudio, max(audio.energy, 0.035f))
        GLES30.glUniform1f(uBlobBass, audio.bass)
        GLES30.glUniform1f(uBlobMid, audio.mid)
        GLES30.glUniform1f(uBlobTreble, audio.treble)
        GLES30.glUniform1f(uBlobBeat, audio.beat)
        GLES30.glUniform3f(uBlobColor, smoothR, smoothG, smoothB)
        GLES30.glUniform1f(
            uBlobIntensity,
            0.38f + audio.energy * 0.28f + audio.beat * 0.22f
        )

        GLES30.glLineWidth(1.2f)
        GLES30.glBindVertexArray(meshVao)
        GLES30.glDrawElements(GLES30.GL_LINES, lineIndexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    @Suppress("LocalVariableName")
    private fun extractBrightPass() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomA.fbo)
        GLES30.glViewport(0, 0, bloomA.width, bloomA.height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(brightProgram)
        bindTexture(0, sceneTarget.texture, uBrightScene)
        GLES30.glUniform1f(
            uBrightThreshold,
            (0.52f - audio.energy * 0.04f).coerceAtLeast(0.42f)
        )
        drawQuad()
    }

    @Suppress("LocalVariableName")
    private fun blurBloom(horizontal: Boolean) {
        val source = if (horizontal) bloomA else bloomB
        val target = if (horizontal) bloomB else bloomA
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.fbo)
        GLES30.glViewport(0, 0, target.width, target.height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(blurProgram)
        bindTexture(0, source.texture, uBlurImage)
        val dx = if (horizontal) 1f / source.width.toFloat() else 0f
        val dy = if (horizontal) 0f else 1f / source.height.toFloat()
        GLES30.glUniform2f(uBlurDirection, dx, dy)
        drawQuad()
    }

    @Suppress("LocalVariableName")
    private fun compositeToScreen() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(compositeProgram)
        bindTexture(0, sceneTarget.texture, uCompositeScene)
        bindTexture(1, bloomA.texture, uCompositeBloom)
        GLES30.glUniform1f(
            uCompositeBloomStrength,
            0.34f + audio.energy * 0.24f + audio.beat * 0.12f
        )
        GLES30.glUniform1f(uCompositeExposure, 0.82f)
        drawQuad()
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(quadVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    @Suppress("LocalVariableName")
    private fun bindTexture(unit: Int, texture: Int, samplerLocation: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(samplerLocation, unit)
    }

    private fun recreateTargets() {
        deleteTarget(sceneTarget)
        deleteTarget(bloomA)
        deleteTarget(bloomB)

        val sceneWidth = max(1, (surfaceWidth * renderScale).toInt())
        val sceneHeight = max(1, (surfaceHeight * renderScale).toInt())
        val bloomWidth = max(1, sceneWidth / 2)
        val bloomHeight = max(1, sceneHeight / 2)

        sceneTarget = createTarget(sceneWidth, sceneHeight, "scene")
        bloomA = createTarget(bloomWidth, bloomHeight, "bloom-a")
        bloomB = createTarget(bloomWidth, bloomHeight, "bloom-b")
        targetsDirty = false
    }

    private fun createTarget(width: Int, height: Int, label: String): RenderTarget {
        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )

        GLES30.glGenFramebuffers(1, framebuffers, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            textures[0],
            0
        )
        GlUtils.checkFramebuffer(label)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return RenderTarget(framebuffers[0], textures[0], width, height)
    }

    private fun deleteTarget(target: RenderTarget) {
        if (target.fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(target.fbo), 0)
        if (target.texture != 0) GLES30.glDeleteTextures(1, intArrayOf(target.texture), 0)
    }

    private fun updateAdaptiveQuality() {
        qualityFrameCounter++
        if (qualityFrameCounter < 180) return
        qualityFrameCounter = 0

        val previous = renderScale
        renderScale = when {
            frameTimeEmaMs > 20.5f -> max(0.55f, renderScale - 0.07f)
            frameTimeEmaMs < 14.2f -> min(0.90f, renderScale + 0.04f)
            else -> renderScale
        }
        if (kotlin.math.abs(previous - renderScale) > 0.001f) targetsDirty = true
    }

    @Suppress("LocalVariableName")
    private fun cacheUniformLocations() {
        uBlobMvp = GLES30.glGetUniformLocation(blobProgram, "uMvp")
        uBlobTime = GLES30.glGetUniformLocation(blobProgram, "uTime")
        uBlobAudio = GLES30.glGetUniformLocation(blobProgram, "uAudio")
        uBlobBass = GLES30.glGetUniformLocation(blobProgram, "uBass")
        uBlobMid = GLES30.glGetUniformLocation(blobProgram, "uMid")
        uBlobTreble = GLES30.glGetUniformLocation(blobProgram, "uTreble")
        uBlobBeat = GLES30.glGetUniformLocation(blobProgram, "uBeat")
        uBlobColor = GLES30.glGetUniformLocation(blobProgram, "uColor")
        uBlobIntensity = GLES30.glGetUniformLocation(blobProgram, "uIntensity")
        uBrightScene = GLES30.glGetUniformLocation(brightProgram, "uScene")
        uBrightThreshold = GLES30.glGetUniformLocation(brightProgram, "uThreshold")
        uBlurImage = GLES30.glGetUniformLocation(blurProgram, "uImage")
        uBlurDirection = GLES30.glGetUniformLocation(blurProgram, "uDirection")
        uCompositeScene = GLES30.glGetUniformLocation(compositeProgram, "uScene")
        uCompositeBloom = GLES30.glGetUniformLocation(compositeProgram, "uBloom")
        uCompositeBloomStrength = GLES30.glGetUniformLocation(compositeProgram, "uBloomStrength")
        uCompositeExposure = GLES30.glGetUniformLocation(compositeProgram, "uExposure")
    }

    private fun fract(value: Float): Float = value - floor(value)

    private fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
        val scaled = h * 6f
        val sector = floor(scaled).toInt()
        val fraction = scaled - floor(scaled)
        val p = v * (1f - s)
        val q = v * (1f - fraction * s)
        val t = v * (1f - (1f - fraction) * s)
        when (sector % 6) {
            0 -> { out[0] = v; out[1] = t; out[2] = p }
            1 -> { out[0] = q; out[1] = v; out[2] = p }
            2 -> { out[0] = p; out[1] = v; out[2] = t }
            3 -> { out[0] = p; out[1] = q; out[2] = v }
            4 -> { out[0] = t; out[1] = p; out[2] = v }
            else -> { out[0] = v; out[1] = p; out[2] = q }
        }
    }

    private data class RenderTarget(
        val fbo: Int,
        val texture: Int,
        val width: Int,
        val height: Int
    ) {
        companion object {
            val EMPTY = RenderTarget(0, 0, 1, 1)
        }
    }
}
