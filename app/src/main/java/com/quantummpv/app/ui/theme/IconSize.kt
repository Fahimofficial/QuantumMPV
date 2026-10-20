/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Icon sizes by role (docs/design/DESIGN_SYSTEM.md). Interactive icons sit in a target of at least
 * [MinTouchTarget] whatever their drawn size, so pass these to the Icon, never to its IconButton.
 */
object IconSize {
  /** Inline with text: metadata, chips, small badges. */
  val Small = 18.dp

  /** Toolbars, sheet and panel actions, list leading icons. */
  val Standard = 24.dp

  /** Secondary transport controls (skip, seek). */
  val Large = 28.dp

  /** Primary transport (play/pause). */
  val Hero = 36.dp

  val MinTouchTarget = 48.dp
}
