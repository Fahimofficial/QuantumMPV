/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates the UPI QR code on the device with ZXing's encoder. The payload never leaves the
 * device: no QR web API is involved.
 */
object UpiQrCode {
  /** Quiet zone around the code, in modules. The QR specification asks for 4. */
  const val QUIET_ZONE_MODULES = 4

  /** Pixels per module for the shared/saved PNG. */
  const val EXPORT_PIXELS_PER_MODULE = 16

  /**
   * Encodes [content] as a QR matrix with one cell per module, quiet zone included.
   * Error correction M (15%) tolerates glare and smudges. No character-set hint: the UPI link is
   * ASCII, so no ECI header is written (some UPI scanners reject codes with one).
   */
  fun encode(content: String): BitMatrix =
    QRCodeWriter().encode(
      content,
      BarcodeFormat.QR_CODE,
      0,
      0,
      mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
      ),
    )

  /** Black-on-white bitmap (in every theme: scanners expect dark on light). */
  fun toBitmap(
    matrix: BitMatrix,
    pixelsPerModule: Int = 1,
  ): Bitmap {
    require(pixelsPerModule > 0) { "pixelsPerModule must be positive" }
    val width = matrix.width * pixelsPerModule
    val height = matrix.height * pixelsPerModule
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      val row = y * width
      val moduleY = y / pixelsPerModule
      for (x in 0 until width) {
        pixels[row + x] = if (matrix[x / pixelsPerModule, moduleY]) Color.BLACK else Color.WHITE
      }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
  }
}
