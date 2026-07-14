package com.quantummpv.app.ui.player

internal class ScreenUnlockPlaybackController {
  private var pendingResumeAfterUnlock = false

  fun onScreenTurnedOff(
    autoplayAfterScreenUnlockEnabled: Boolean,
    wasPlayingBeforePause: Boolean,
    isCurrentlyPaused: Boolean?,
    backgroundPlaybackActive: Boolean,
    isUserFinishing: Boolean,
    isFinishing: Boolean,
  ) {
    val wasPlayingWhenScreenTurnedOff = wasPlayingBeforePause || isCurrentlyPaused == false

    pendingResumeAfterUnlock =
      autoplayAfterScreenUnlockEnabled &&
        wasPlayingWhenScreenTurnedOff &&
        !backgroundPlaybackActive &&
        !isUserFinishing &&
        !isFinishing
  }

  fun hasPendingResume(): Boolean = pendingResumeAfterUnlock

  fun consumeResumeAfterUnlockIfReady(isDeviceLocked: Boolean): Boolean {
    if (!pendingResumeAfterUnlock || isDeviceLocked) return false

    pendingResumeAfterUnlock = false
    return true
  }
}
