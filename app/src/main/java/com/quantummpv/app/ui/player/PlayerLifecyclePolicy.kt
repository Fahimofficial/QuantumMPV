package com.quantummpv.app.ui.player

internal object PlayerLifecyclePolicy {
  fun shouldPauseOnPause(
    backgroundPlaybackEnabled: Boolean,
    isUserFinishing: Boolean,
    isInPictureInPictureMode: Boolean,
    isScreenOffOrLocked: Boolean,
  ): Boolean {
    if (isUserFinishing) return true
    if (isInPictureInPictureMode && !isScreenOffOrLocked) return false

    return !backgroundPlaybackEnabled
  }

  fun shouldKeepBackgroundPlaybackAliveOnDestroy(
    backgroundPlaybackEnabled: Boolean,
    backgroundPlaybackSessionActive: Boolean,
  ): Boolean = backgroundPlaybackEnabled && backgroundPlaybackSessionActive

  fun shouldTreatStopAsPipDismissal(
    wasInPictureInPictureMode: Boolean,
    isInPictureInPictureMode: Boolean,
    isChangingConfigurations: Boolean,
    backgroundPlaybackEnabled: Boolean,
    isScreenOffOrLocked: Boolean,
    alreadyHandled: Boolean,
  ): Boolean =
    wasInPictureInPictureMode &&
      !isInPictureInPictureMode &&
      !isChangingConfigurations &&
      !backgroundPlaybackEnabled &&
      !isScreenOffOrLocked &&
      !alreadyHandled

  fun shouldStartBackgroundPlaybackOnStop(
    backgroundPlaybackEnabled: Boolean,
    backgroundPlaybackSessionActive: Boolean,
    isUserFinishing: Boolean,
    isFinishing: Boolean,
    isInPictureInPictureMode: Boolean,
    isScreenOffOrLocked: Boolean,
  ): Boolean =
    backgroundPlaybackEnabled &&
      !backgroundPlaybackSessionActive &&
      !isUserFinishing &&
      !isFinishing &&
      (!isInPictureInPictureMode || isScreenOffOrLocked)
}
