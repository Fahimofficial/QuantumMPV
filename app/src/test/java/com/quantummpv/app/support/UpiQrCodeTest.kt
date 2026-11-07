package com.quantummpv.app.support

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Decodes the exact pixels the app renders and checks they carry the UPI link. */
class UpiQrCodeTest {
  private fun decode(qr: UpiQrCode.Pixels): String {
    val source = RGBLuminanceSource(qr.width, qr.height, qr.argb)
    val hints = mapOf(DecodeHintType.PURE_BARCODE to false, DecodeHintType.TRY_HARDER to true)
    return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
  }

  @Test
  fun `generated qr decodes to the configured upi uri`() {
    val qr = UpiQrCode.encode(SupportConfig.upiPaymentUri, 768)
    val text = decode(qr)
    assertEquals("upi://pay?pa=SimplyFahim@sbi&pn=Md%20Fahim&cu=INR", text)
    val params = UpiPaymentUri.parse(text)!!
    assertEquals("SimplyFahim@sbi", params["pa"])
    assertEquals("Md Fahim", params["pn"])
    assertEquals("INR", params["cu"])
    assertNull(params["am"])
  }

  @Test
  fun `qr is square, black on white and readable at small sizes`() {
    listOf(160, 300, 768).forEach { size ->
      val qr = UpiQrCode.encode(SupportConfig.upiPaymentUri, size)
      assertEquals(qr.width, qr.height)
      assertEquals(setOf(UpiQrCode.DARK, UpiQrCode.LIGHT), qr.argb.toSet())
      assertEquals(SupportConfig.upiPaymentUri, decode(qr))
    }
  }

  /** Writes the PNG so an independent decoder (zxing-cpp / zbar) can check it outside the JVM. */
  @Test
  fun `export png for external verification`() {
    val qr = UpiQrCode.encode(SupportConfig.upiPaymentUri, 768)
    val image = BufferedImage(qr.width, qr.height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, qr.width, qr.height, qr.argb, 0, qr.width)
    val out = File("build/support-qr/upi-qr.png").apply { parentFile?.mkdirs() }
    ImageIO.write(image, "png", out)
  }
}
