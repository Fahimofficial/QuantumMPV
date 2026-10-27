/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.data.network.client

import android.content.Context
import android.content.SharedPreferences
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import com.quantummpv.app.domain.network.SftpHostKeyChallenge
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** Thrown by [SftpClient.connect] until the user trusts the server's host key. */
class SftpHostKeyException(
  val challenge: SftpHostKeyChallenge,
) : java.io.IOException(
    if (challenge.isChanged) {
      "The SFTP server's host key has changed"
    } else {
      "The SFTP server's host key is not trusted yet"
    },
  )

/** Trust-on-first-use store of SFTP host keys, keyed by `host:port` like OpenSSH known_hosts. */
interface SftpKnownHosts {
  fun trustedKey(hostPort: String): String?

  fun trust(
    hostPort: String,
    key: String,
  )

  fun forget(hostPort: String)
}

class SharedPreferencesSftpKnownHosts(
  private val preferences: SharedPreferences,
) : SftpKnownHosts {
  constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

  override fun trustedKey(hostPort: String): String? = preferences.getString(normalize(hostPort), null)

  override fun trust(
    hostPort: String,
    key: String,
  ) {
    preferences.edit().putString(normalize(hostPort), key).apply()
  }

  override fun forget(hostPort: String) {
    preferences.edit().remove(normalize(hostPort)).apply()
  }

  companion object {
    const val PREFERENCES_NAME = "sftp_known_hosts"
  }
}

object SftpHostKeyFormat {
  fun hostPort(
    host: String,
    port: Int,
  ): String = normalize("${host.trim().removePrefix("[").removeSuffix("]")}:$port")

  /** OpenSSH-style `SHA256:<unpadded base64>` fingerprint of a raw key blob. */
  fun fingerprint(key: ByteArray): String =
    "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

  fun fingerprint(base64Key: String): String = fingerprint(Base64.getDecoder().decode(base64Key))

  fun encode(key: ByteArray): String = Base64.getEncoder().encodeToString(key)
}

private fun normalize(hostPort: String): String = hostPort.trim().lowercase(Locale.ROOT)

/**
 * JSch host-key repository that only accepts the key stored for one connection's `host:port`.
 * Anything else makes JSch abort the handshake (`StrictHostKeyChecking=yes`) before any
 * credential is sent; the presented key is kept so the caller can ask the user to trust it.
 */
internal class StrictSftpHostKeyRepository(
  private val hostPort: String,
  private val knownHosts: SftpKnownHosts,
) : HostKeyRepository {
  @Volatile
  var rejection: SftpHostKeyChallenge? = null
    private set

  override fun check(
    host: String?,
    key: ByteArray?,
  ): Int {
    if (key == null) return HostKeyRepository.NOT_INCLUDED
    val presented = SftpHostKeyFormat.encode(key)
    val trusted = knownHosts.trustedKey(hostPort)
    return when {
      trusted == presented -> HostKeyRepository.OK
      else -> {
        rejection =
          SftpHostKeyChallenge(
            hostPort = hostPort,
            presentedKey = presented,
            fingerprint = SftpHostKeyFormat.fingerprint(key),
            previousFingerprint = trusted?.let { runCatching { SftpHostKeyFormat.fingerprint(it) }.getOrDefault("unknown") },
          )
        if (trusted == null) HostKeyRepository.NOT_INCLUDED else HostKeyRepository.CHANGED
      }
    }
  }

  // Keys are only ever added after the user confirms them in the UI.
  override fun add(
    hostkey: HostKey?,
    ui: UserInfo?,
  ) = Unit

  override fun remove(
    host: String?,
    type: String?,
  ) = Unit

  override fun remove(
    host: String?,
    type: String?,
    key: ByteArray?,
  ) = Unit

  override fun getKnownHostsRepositoryID(): String = "quantummpv-sftp-known-hosts"

  override fun getHostKey(): Array<HostKey> = trustedHostKeys()

  override fun getHostKey(
    host: String?,
    type: String?,
  ): Array<HostKey> = trustedHostKeys().filter { type == null || it.type == type }.toTypedArray()

  private fun trustedHostKeys(): Array<HostKey> {
    val trusted = knownHosts.trustedKey(hostPort) ?: return emptyArray()
    return runCatching { arrayOf(HostKey(hostPort, Base64.getDecoder().decode(trusted))) }.getOrDefault(emptyArray())
  }
}
