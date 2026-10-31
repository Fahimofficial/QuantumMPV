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
 * Single source of truth for the "Support My Work" screen.
 *
 * Update links or payment details here only. The screen, the generated UPI QR code, the share
 * text and the intents all read from this object. Nothing here is sent to a server: the app only
 * opens these links in apps the user picks.
 */
object SupportConfig {
  const val DEVELOPER_NAME = "SimplyFahim"

  const val KOFI_URL = "https://ko-fi.com/simplyfahim"

  const val UPI_ID = "SimplyFahim@sbi"
  const val UPI_PAYEE_NAME = "SimplyFahim"
  const val UPI_CURRENCY = "INR"

  const val WEBSITE_URL = "https://SimplyFahim.pages.dev"
  const val GITHUB_PROFILE_URL = "https://github.com/Fahimofficial/"

  /** This app's own public repository. Each app links to its own repository, never a shared one. */
  const val PROJECT_REPOSITORY_URL = "https://github.com/Fahimofficial/QuantumMPV"
  const val PROJECT_LICENSE = "AGPL-3.0-or-later"

  /** `upi://pay?pa=SimplyFahim@sbi&pn=SimplyFahim&cu=INR`, with no amount so the payer chooses it. */
  val upiPaymentUri: String =
    UpiPaymentUri.build(
      payeeAddress = UPI_ID,
      payeeName = UPI_PAYEE_NAME,
      currency = UPI_CURRENCY,
    )

  /** Strips the scheme and trailing slash for display, e.g. `ko-fi.com/simplyfahim`. */
  fun displayUrl(url: String): String = url.substringAfter("://").trimEnd('/')
}
