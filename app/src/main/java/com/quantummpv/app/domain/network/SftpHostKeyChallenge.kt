/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.domain.network

/** An SFTP server host key the user has not confirmed yet, or one that differs from the trusted key. */
data class SftpHostKeyChallenge(
  val hostPort: String,
  /** Base64 of the raw public key blob, as sent by the server. */
  val presentedKey: String,
  val fingerprint: String,
  val previousFingerprint: String?,
) {
  val isChanged: Boolean get() = previousFingerprint != null
}
