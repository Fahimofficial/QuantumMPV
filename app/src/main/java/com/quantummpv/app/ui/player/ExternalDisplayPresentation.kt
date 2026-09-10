package com.quantummpv.app.ui.player

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

class ExternalDisplayPresentation(
  context: Context,
  display: Display,
) : Presentation(context, display) {
  private val surfaceOwner = Any()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window?.decorView?.setBackgroundColor(android.graphics.Color.BLACK)

    val surfaceView = SurfaceView(context)
    surfaceView.setBackgroundColor(android.graphics.Color.BLACK)
    surfaceView.holder.addCallback(
      object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
          PlaybackSession.bindSurface(
            surface = holder.surface,
            width = surfaceView.width,
            height = surfaceView.height,
            owner = surfaceOwner,
            ownerIsActive = { isShowing },
          )
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
          PlaybackSession.resizeSurface(width, height, surfaceOwner)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
          PlaybackSession.unbindSurface(surfaceOwner)
        }
      },
    )
    setContentView(
      surfaceView,
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
  }
}
