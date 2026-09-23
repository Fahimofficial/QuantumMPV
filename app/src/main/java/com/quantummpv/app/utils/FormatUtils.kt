package com.quantummpv.app.utils

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
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
