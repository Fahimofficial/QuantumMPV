/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences.preference

import android.util.Log
import com.quantummpv.app.data.network.credentials.AndroidNetworkCredentialKey
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Stores a secret (API key or token) encrypted with the app's Android Keystore key while keeping
 * the plain [Preference] API for callers. Values written by earlier builds in plaintext are
 * re-encrypted on first read. If the key is gone (for example after restoring a backup onto a new
 * device) the value reads as empty so the user is simply asked to sign in again.
 */
class EncryptedStringPreference(
  private val delegate: Preference<String>,
  private val cipher: NetworkCredentialCipher = NetworkCredentialCipher(AndroidNetworkCredentialKey::getOrCreate),
) : Preference<String> {
  override fun key(): String = delegate.key()

  override fun get(): String {
    val stored = delegate.get()
    if (stored.isEmpty()) return ""
    if (cipher.isEncrypted(stored)) return decode(stored)
    // Legacy plaintext: migrate in place, but never lose the value if encryption is unavailable.
    encryptOrNull(stored)?.let(delegate::set)
    return stored
  }

  override fun set(value: String) {
    delegate.set(if (value.isEmpty()) "" else encryptOrNull(value) ?: value)
  }

  override fun isSet(): Boolean = delegate.isSet()

  override fun delete() = delegate.delete()

  override fun defaultValue(): String = delegate.defaultValue()

  override fun changes(): Flow<String> =
    delegate.changes().map { stored -> if (stored.isEmpty() || !cipher.isEncrypted(stored)) stored else decode(stored) }
      .distinctUntilChanged()

  override fun stateIn(scope: CoroutineScope): StateFlow<String> = changes().stateIn(scope, SharingStarted.Eagerly, get())

  private fun decode(stored: String): String =
    runCatching { cipher.decrypt(stored) }
      .onFailure { Log.w(TAG, "Stored secret ${key()} can no longer be decrypted; treating it as unset") }
      .getOrDefault("")

  private fun encryptOrNull(value: String): String? =
    runCatching { cipher.encrypt(value) }
      .onFailure { Log.e(TAG, "Keystore unavailable; ${key()} kept unencrypted", it) }
      .getOrNull()

  private companion object {
    const val TAG = "EncryptedPreference"
  }
}

/** Wraps a string preference so its value is encrypted at rest. */
fun Preference<String>.encrypted(): Preference<String> = EncryptedStringPreference(this)
