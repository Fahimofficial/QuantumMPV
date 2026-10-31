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
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the on-device UPI QR code through the same Android Bitmap path the Support screen
 * uses, decodes the pixels with ZXing, and writes the PNG to build/support-qr/ so it can be
 * checked with an independent decoder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UpiQrCodeTest {
  private val payload = SupportConfig.upiPaymentUri()

  @Test
  fun `rendered QR bitmap decodes back to the UPI link`() {
    val bitmap = UpiQrCode.toBitmap(UpiQrCode.encode(payload), UpiQrCode.EXPORT_PIXELS_PER_MODULE)

    assertEquals(payload, decode(bitmap))

    val out = File("build/support-qr/upi-qr.png").apply { parentFile?.mkdirs() }
    out.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
  }

  @Test
  fun `QR code keeps the four-module quiet zone`() {
    val matrix = UpiQrCode.encode(payload)
    val quiet = UpiQrCode.QUIET_ZONE_MODULES
    for (i in 0 until matrix.width) {
      for (q in 0 until quiet) {
        assertFalse(matrix[i, q])
        assertFalse(matrix[q, i])
        assertFalse(matrix[i, matrix.height - 1 - q])
        assertFalse(matrix[matrix.width - 1 - q, i])
      }
    }
    assertTrue(matrix[quiet, quiet])
  }

  private fun decode(bitmap: Bitmap): String {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
  }
}
