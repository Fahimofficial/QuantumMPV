package com.quantummpv.app.ui.player.visualizer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class BlobVisualizerView(
    context: Context,
    features: AudioFeatures = AudioFeatures(),
) : GLSurfaceView(context) {
    private val blobRenderer = BlobRenderer(context.applicationContext, features)

    private var previousX = 0f
    private var previousY = 0f

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(blobRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousX
                val dy = event.y - previousY
                previousX = event.x
                previousY = event.y
                blobRenderer.addTouchRotation(dx / width.coerceAtLeast(1), dy / height.coerceAtLeast(1))
                return true
            }
            MotionEvent.ACTION_UP -> {
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
