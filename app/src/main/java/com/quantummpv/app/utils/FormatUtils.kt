package com.quantummpv.app.utils

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
  fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0s"
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return when {
      hours > 0 -> String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainingSeconds)
      minutes > 0 -> String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
      else -> "${remainingSeconds}s"
    }
  }

  fun formatFileSize(
    bytes: Long,
    unknownLabel: String = "0 B",
  ): String {
    if (bytes <= 0) return unknownLabel
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)

    // For "B" (bytes), we usually don't want decimal points. For simplicity and matching most occurrences:
    return if (digitGroups == 0) {
      "$bytes B"
    } else {
      String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups],
      )
    }
  }
}
