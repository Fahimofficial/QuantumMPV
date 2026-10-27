/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

/**
 * Separates the user's subtitle visibility from temporary hiding by the app.
 *
 * The mini player hides subtitles while video renders in a small surface. That is not a user
 * choice, so it must neither be saved as one nor be undone by forcing subtitles back on: when the
 * mini player goes away the value the user had before is restored, whatever it was.
 */
object SubtitleVisibility {
  private val lock = Any()
  private var suppressions = 0
  private var userValueWhileSuppressed: Boolean? = null

  /** The visibility the user chose, ignoring any temporary suppression. Null when unknown. */
  fun userValue(): Boolean? =
    synchronized(lock) {
      if (suppressions > 0) userValueWhileSuppressed else PlaybackSession.getPropertyBoolean("sub-visibility")
    }

  /** Applies a visibility the user chose (or that was restored for them). */
  fun applyUserValue(visible: Boolean) {
    synchronized(lock) {
      if (suppressions > 0) {
        userValueWhileSuppressed = visible
      } else {
        PlaybackSession.setPropertyBoolean("sub-visibility", visible)
      }
    }
  }

  /** Temporarily hides subtitles; balance every call with [release]. */
  fun suppress() {
    synchronized(lock) {
      if (suppressions++ == 0) {
        userValueWhileSuppressed = PlaybackSession.getPropertyBoolean("sub-visibility")
        PlaybackSession.setPropertyBoolean("sub-visibility", false)
      }
    }
  }

  fun release() {
    synchronized(lock) {
      if (suppressions == 0) return
      if (--suppressions == 0) {
        userValueWhileSuppressed?.let { PlaybackSession.setPropertyBoolean("sub-visibility", it) }
        userValueWhileSuppressed = null
      }
    }
  }
}
