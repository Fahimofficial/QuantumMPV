/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SupportConfigTest {
  @Test
  fun `UPI link identifies the payee only, with no amount`() {
    assertEquals("upi://pay?pa=SimplyFahim@sbi&pn=SimplyFahim&cu=INR", SupportConfig.upiPaymentUri())
    assertFalse(SupportConfig.upiPaymentUri().contains("am="))
  }

  @Test
  fun `UPI link percent-encodes unsafe characters`() {
    assertEquals(
      "upi://pay?pa=a%26b@bank&pn=Jane%20Doe&cu=INR",
      SupportConfig.upiPaymentUri(upiId = "a&b@bank", payeeName = "Jane Doe"),
    )
  }

  @Test
  fun `support details are the developer's own and the repo is QuantumMPV's`() {
    assertEquals("SimplyFahim@sbi", SupportConfig.UPI_ID)
    assertEquals("SimplyFahim", SupportConfig.UPI_PAYEE_NAME)
    assertEquals("INR", SupportConfig.UPI_CURRENCY)
    assertEquals("https://ko-fi.com/simplyfahim", SupportConfig.KOFI_URL)
    assertEquals("https://SimplyFahim.pages.dev", SupportConfig.WEBSITE)
    assertEquals("https://github.com/Fahimofficial/", SupportConfig.GITHUB_PROFILE)
    assertEquals("https://github.com/Fahimofficial/QuantumMPV", SupportConfig.APP_REPOSITORY)
  }
}
