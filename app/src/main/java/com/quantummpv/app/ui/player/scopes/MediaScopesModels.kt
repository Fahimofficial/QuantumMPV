/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player.scopes

// Inspired by https://github.com/aagedal/Aagedal-Media-Player.
enum class MediaScopeTab {
  Audio,
  Video,
}

enum class VideoScopeMode {
  LumaWaveform,
  RgbyParade,
  Vectorscope,
}

data class MediaScopesUiState(
  val overlayVisible: Boolean = false,
  val tab: MediaScopeTab = MediaScopeTab.Video,
  val videoMode: VideoScopeMode = VideoScopeMode.LumaWaveform,
  val expanded: Boolean = false,
  val analysisResolution: Int = 360,
  val frameRate: Int = 5,
)
