package com.quantummpv.app.ui.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class ExternalDisplayManager(
  context: Context,
  private val onStateChanged: (Boolean) -> Unit,
) {
  private val appContext = context.applicationContext
  private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  private val handler = Handler(Looper.getMainLooper())
  private var presentation: ExternalDisplayPresentation? = null
  private var released = false

  var enabled = true
    set(value) {
      field = value
      reconcile()
    }

  val isActive: Boolean
    get() = presentation != null

  private val displayListener =
    object : DisplayManager.DisplayListener {
      override fun onDisplayAdded(displayId: Int) = reconcile()

      override fun onDisplayRemoved(displayId: Int) = reconcile()

      override fun onDisplayChanged(displayId: Int) = reconcile()
    }

  fun start() {
    released = false
    displayManager.registerDisplayListener(displayListener, handler)
    reconcile()
  }

  fun release() {
    released = true
    displayManager.unregisterDisplayListener(displayListener)
    presentation?.dismiss()
    presentation = null
    onStateChanged(false)
  }

  private fun reconcile() {
    if (released) return

    val target =
      if (enabled) {
        displayManager
          .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
          .firstOrNull()
      } else {
        null
      }
    val currentDisplayId = presentation?.display?.displayId

    if (target == null || target.displayId != currentDisplayId) {
      presentation?.dismiss()
      presentation = null
      if (currentDisplayId != null) onStateChanged(false)
    }

    if (target != null && presentation == null) {
      Log.d(TAG, "Showing external display on ${target.name} (${target.displayId})")
      val shown = ExternalDisplayPresentation(appContext, target)
      shown.setOnDismissListener {
        if (presentation === shown) {
          presentation = null
          onStateChanged(false)
          reconcile()
        }
      }
      presentation = shown
      shown.show()
      onStateChanged(true)
    }
  }

  companion object {
    private const val TAG = "ExternalDisplayManager"
  }
}
