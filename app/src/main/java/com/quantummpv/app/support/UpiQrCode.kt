/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates QR codes on the device with ZXing's encoder. No network, no third-party service.
 *
 * The output is a plain ARGB pixel array so it can be unit tested on the JVM and turned into an
 * Android `Bitmap` with `Bitmap.createBitmap(pixels, width, height, ARGB_8888)`.
 */
object UpiQrCode {
  const val DARK: Int = 0xFF000000.toInt()
  const val LIGHT: Int = 0xFFFFFFFF.toInt()

  /** Four-module quiet zone, as required by ISO/IEC 18004 for reliable scanning. */
  private const val QUIET_ZONE_MODULES = 4

  class Pixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
  )

  fun encode(
    content: String,
    sizePx: Int,
  ): Pixels {
    require(sizePx > 0) { "sizePx must be positive" }
    require(content.all { it.code in 0x20..0x7E }) { "QR content must be printable ASCII" }
    val hints =
      mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        // No CHARACTER_SET hint: UPI links are ASCII, and an ECI header confuses some scanners.
      )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val row = y * width
      for (x in 0 until width) {
        pixels[row + x] = if (matrix[x, y]) DARK else LIGHT
      }
    }
    return Pixels(width, height, pixels)
  }
}
