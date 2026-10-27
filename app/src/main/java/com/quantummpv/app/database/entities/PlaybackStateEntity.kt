/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PlaybackStateEntity(
  @PrimaryKey val mediaTitle: String,
  val lastPosition: Int, // in seconds
  val playbackSpeed: Double,
  val videoZoom: Float = 0f,
  val sid: Int,
  val secondarySid: Int = -1, // Secondary subtitle track ID (-1 means disabled)
  val subDelay: Int,
  val subSpeed: Double,
  val aid: Int,
  val audioDelay: Int,
  val timeRemaining: Int = 0, // in seconds (duration - lastPosition)
  val externalSubtitles: String = "", // Comma-separated list of external subtitle URIs
  val hasBeenWatched: Boolean = false, // Persistent flag: true if video has ever reached the watched threshold
  val newLabelOverride: Boolean? = null,
  /** User's subtitle visibility for this file; null = never saved, leave mpv's value alone. */
  val subVisibility: Boolean? = null,
  /** Stable identity of the selected subtitle track (see TrackKey); survives track re-numbering. */
  val sidKey: String? = null,
  val secondarySidKey: String? = null,
  val aidKey: String? = null,
  /** Last known location (local path or URI) so rows for vanished files can be swept. */
  val mediaLocation: String? = null,
)
