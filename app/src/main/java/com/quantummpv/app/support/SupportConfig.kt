/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.support

/**
 * "Support my work" configuration: the developer's payment and contact details, and this app's
 * own repository. This is the only place these values live, so updating a link is a one-line
 * change.
 *
 * Nothing here is sent anywhere by the app: links open in apps the user picks, and the UPI QR
 * code is generated on the device.
 */
object SupportConfig {
  /** Ko-fi profile (account "SimplyFahim"). */
  const val KOFI_URL = "https://ko-fi.com/simplyfahim"

  /** The developer's personal website. Not a project page. */
  const val WEBSITE = "https://SimplyFahim.pages.dev"

  /** The developer's GitHub profile, listing all public projects. */
  const val GITHUB_PROFILE = "https://github.com/Fahimofficial/"

  /** QuantumMPV's own repository. */
  const val APP_REPOSITORY = "https://github.com/Fahimofficial/QuantumMPV"

  /** Public UPI ID (VPA) and the payee name UPI apps show before the user confirms. */
  const val UPI_ID = "SimplyFahim@sbi"
  const val UPI_PAYEE_NAME = "SimplyFahim"
  const val UPI_CURRENCY = "INR"

  /**
   * Standard UPI payment link that only identifies the payee:
   * `upi://pay?pa=SimplyFahim@sbi&pn=SimplyFahim&cu=INR`.
   *
   * No amount (`am`) is ever set: the user enters it and confirms in their own UPI app.
   * The same string is the "Open UPI app" intent data and the QR code payload. `@` stays literal
   * (valid in a URI query, and what bank-issued UPI QR codes use); any other character outside
   * the RFC 3986 unreserved set is percent-encoded.
   */
  fun upiPaymentUri(
    upiId: String = UPI_ID,
    payeeName: String = UPI_PAYEE_NAME,
    currency: String = UPI_CURRENCY,
  ): String = "upi://pay?pa=${encode(upiId)}&pn=${encode(payeeName)}&cu=${encode(currency)}"

  private val KEPT_AS_IS: Set<Char> =
    (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~', '@')).toSet()

  private fun encode(value: String): String =
    buildString {
      value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xFF
        val c = unsigned.toChar()
        if (c in KEPT_AS_IS) append(c) else append("%%%02X".format(unsigned))
      }
    }
}
