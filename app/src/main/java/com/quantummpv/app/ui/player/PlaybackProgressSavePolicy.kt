/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

/** Rules for the periodic resume-position save that runs while the player is in the foreground. */
internal object PlaybackProgressSavePolicy {
  const val INTERVAL_MS = 15_000L

  fun shouldSave(
    mpvInitialized: Boolean,
    isExiting: Boolean,
    ownsSession: Boolean,
    isForeground: Boolean,
    mediaTitle: String,
  ): Boolean = mpvInitialized && !isExiting && ownsSession && isForeground && mediaTitle.isNotBlank()
}
