package com.quantummpv.app.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportConfigTest {
  @Test
  fun `official support details are exact`() {
    assertEquals("https://ko-fi.com/simplyfahim", SupportConfig.KOFI_URL)
    assertEquals("SimplyFahim@sbi", SupportConfig.UPI_ID)
    assertEquals("Md Fahim", SupportConfig.UPI_PAYEE_NAME)
    assertEquals("INR", SupportConfig.UPI_CURRENCY)
    assertEquals("https://SimplyFahim.pages.dev", SupportConfig.WEBSITE_URL)
    assertEquals("https://github.com/Fahimofficial/", SupportConfig.GITHUB_PROFILE_URL)
  }

  @Test
  fun `repository link points at this app's own repository`() {
    assertEquals("https://github.com/Fahimofficial/QuantumMPV", SupportConfig.PROJECT_REPOSITORY_URL)
    assertFalse(SupportConfig.PROJECT_REPOSITORY_URL == SupportConfig.WEBSITE_URL)
  }

  @Test
  fun `upi uri has payee, name and currency but no amount`() {
    assertEquals("upi://pay?pa=SimplyFahim@sbi&pn=Md%20Fahim&cu=INR", SupportConfig.upiPaymentUri)
    val params = UpiPaymentUri.parse(SupportConfig.upiPaymentUri)!!
    assertEquals(mapOf("pa" to "SimplyFahim@sbi", "pn" to "Md Fahim", "cu" to "INR"), params)
    assertNull(params["am"])
  }

  @Test
  fun `builder percent-encodes names with spaces and rejects invalid ids`() {
    assertEquals(
      "upi://pay?pa=a.b@okaxis&pn=A%20B%26C&cu=INR",
      UpiPaymentUri.build("a.b@okaxis", "A B&C"),
    )
    assertTrue(UpiPaymentUri.isValidVpa("SimplyFahim@sbi"))
    assertFalse(UpiPaymentUri.isValidVpa("no-at-sign"))
    assertFalse(UpiPaymentUri.isValidVpa("x@"))
  }

  @Test
  fun `old project-specific donation details are gone`() {
    val all =
      listOf(
        SupportConfig.KOFI_URL,
        SupportConfig.UPI_ID,
        SupportConfig.upiPaymentUri,
        SupportConfig.WEBSITE_URL,
      ).joinToString()
    listOf("adityanarvekar", "aadinarvekar", "aadiinarvekar", "paypal", "@Sbi", "vercel.app").forEach {
      assertFalse("found $it", all.contains(it))
    }
  }

  @Test
  fun `display url strips scheme and trailing slash`() {
    assertEquals("ko-fi.com/simplyfahim", SupportConfig.displayUrl(SupportConfig.KOFI_URL))
    assertEquals("github.com/Fahimofficial", SupportConfig.displayUrl(SupportConfig.GITHUB_PROFILE_URL))
  }
}
