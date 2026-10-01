/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.browser.networkstreaming

import android.net.Uri
import com.quantummpv.app.domain.media.model.Video
import com.quantummpv.app.domain.network.NetworkFile
import com.quantummpv.app.domain.network.NetworkPlaybackUri
import com.quantummpv.app.utils.FormatUtils

/** Converts a remote file to a playlist row without persisting connection credentials or tokens. */
internal fun NetworkFile.toPlaylistVideo(connectionId: Long): Video {
  val isAudio = isPlayableNetworkAudio()
  val persistentUri = NetworkPlaybackUri.create(connectionId, path)
  val safeSize = size.coerceAtLeast(0L)
  val stableId = ("$connectionId\u0000$path").hashCode().toLong().and(Long.MAX_VALUE).coerceAtLeast(1L)

  return Video(
    id = stableId,
    title = name,
    displayName = name,
    path = persistentUri,
    uri = Uri.parse(persistentUri),
    duration = 0L,
    durationFormatted = "--",
    size = safeSize,
    sizeFormatted = FormatUtils.formatFileSize(safeSize),
    dateModified = lastModified,
    dateAdded = lastModified,
    mimeType = mimeType?.takeIf(String::isNotBlank) ?: if (isAudio) "audio/*" else "video/*",
    bucketId = "network:$connectionId",
    bucketDisplayName = "",
    width = 0,
    height = 0,
    fps = 0f,
    resolution = "--",
    isAudio = isAudio,
  )
}
