/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

import java.nio.charset.StandardCharsets

/**
 * Builds and parses UPI deep links (`upi://pay?...`) as described in the NPCI UPI linking spec.
 *
 * Only `pa` (payee address), `pn` (payee name) and `cu` (currency) are set. There is deliberately
 * no `am` (amount), so every UPI app asks the payer how much to send.
 */
object UpiPaymentUri {
  private const val SCHEME_PREFIX = "upi://pay?"

  /** `handle@psp`, e.g. `name@sbi`. Matches the characters NPCI allows in a VPA. */
  private val VPA_PATTERN = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$")

  fun isValidVpa(vpa: String): Boolean = VPA_PATTERN.matches(vpa)

  fun build(
    payeeAddress: String,
    payeeName: String,
    currency: String = "INR",
  ): String {
    require(isValidVpa(payeeAddress)) { "Invalid UPI ID: $payeeAddress" }
    require(payeeName.isNotBlank()) { "Payee name must not be blank" }
    require(currency.length == 3) { "Currency must be an ISO 4217 code" }
    return SCHEME_PREFIX +
      "pa=${encode(payeeAddress)}" +
      "&pn=${encode(payeeName)}" +
      "&cu=${encode(currency)}"
  }

  /** Returns the decoded query parameters of a `upi://pay` link, or null if it is not one. */
  fun parse(uri: String): Map<String, String>? {
    if (!uri.startsWith(SCHEME_PREFIX)) return null
    return uri
      .removePrefix(SCHEME_PREFIX)
      .split('&')
      .filter { it.isNotEmpty() }
      .associate { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', missingDelimiterValue = "")
        key to java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name())
      }
  }

  /**
   * Percent-encodes everything except RFC 3986 unreserved characters and `@`. `@` is legal in a
   * query and is kept literal because some UPI apps fail to match `%40` in a scanned VPA.
   */
  private fun encode(value: String): String =
    buildString {
      value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-._~@")) {
          append(ch)
        } else {
          append('%').append("%02X".format(c))
        }
      }
    }
}
