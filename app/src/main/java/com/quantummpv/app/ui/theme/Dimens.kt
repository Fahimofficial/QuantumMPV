/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.ui.theme

import androidx.compose.ui.unit.dp

/** Sizes used by the motion and list-polish work; general spacing stays in [Spacing]. */
object Dimens {
  const val PRESSED_SCALE = 0.98f
  val PressedLift = 2.dp
  val MinTouchTarget = 48.dp

  /** Distance list rows travel while fading in during the first-open stagger. */
  val StaggerOffset = 16.dp

  /** One-time playlist handle nudge distance. */
  val HandleNudge = 12.dp

  /** Watched-progress bar on video thumbnails. */
  val ThumbnailProgressHeight = 5.dp

  /** Reserved height for the lazily loaded "1080p · HEVC" chip row so rows never shift. */
  val MetadataChipRowHeight = 22.dp

  val SelectionBorder = 2.dp
  val SelectionGlow = 8.dp

  /** Header parallax factor on About: the header moves at half the scroll speed. */
  const val ABOUT_HEADER_PARALLAX = 0.5f

  /** Dim behind the home overflow popup menu. */
  const val MENU_SCRIM_ALPHA = 0.32f
}
