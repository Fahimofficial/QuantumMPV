/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.utils.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Keyed MAC whose key never leaves this device, so stored PIN hashes cannot be brute-forced offline. */
fun interface PinKeyMac {
  /** Returns the MAC of [data], or null when no device-bound key is available. */
  fun mac(data: ByteArray): ByteArray?
}

/** HMAC-SHA256 with a non-exportable Android Keystore key. */
object KeystorePinKeyMac : PinKeyMac {
  private const val KEYSTORE = "AndroidKeyStore"
  private const val ALIAS = "quantummpv.secure-folder-pin.v1"

  @Synchronized
  private fun key(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
    generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
    return generator.generateKey()
  }

  override fun mac(data: ByteArray): ByteArray? =
    runCatching {
      Mac.getInstance("HmacSHA256").run {
        init(key())
        doFinal(data)
      }
    }.onFailure { Log.w("PinHasher", "Keystore HMAC unavailable; using PBKDF2 only", it) }.getOrNull()
}

/**
 * Hashing for the Secure Folder PIN and security-question answer.
 *
 * Current format (`v2$…`): PBKDF2-HMAC-SHA256 of the salted value, then HMAC-SHA256 with a
 * non-exportable Keystore key. A copied preferences file is useless on another device, and on this
 * device guesses go through [com.quantummpv.app.preferences.SecureFolderPreferences]'s attempt
 * throttling. When the Keystore is unavailable a slower PBKDF2-only hash is stored instead.
 *
 * Legacy hashes (single salted SHA-256, no prefix) still verify and are upgraded on the next
 * successful unlock. Raw PINs and answers are never stored.
 */
object PinHasher {
  private const val SALT_BYTES = 16
  private const val PREFIX_KEYED = "v2\$km\$"
  private const val PREFIX_PBKDF2 = "v2\$pb\$"

  /** Iterations in front of the device-bound MAC: the MAC already blocks offline guessing. */
  private const val KEYED_ITERATIONS = 10_000

  /** Fallback without a device-bound key: as slow as a synchronous unlock can reasonably afford. */
  private const val PBKDF2_ONLY_ITERATIONS = 120_000

  /** Generates a new random salt, Base64-encoded for storage in SharedPreferences. */
  fun generateSalt(): String {
    val bytes = ByteArray(SALT_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  /** Produces a current-format hash of [value] with [salt]. */
  fun hash(
    value: String,
    salt: String,
    keyMac: PinKeyMac = KeystorePinKeyMac,
  ): String {
    val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
    val stretched = pbkdf2(value.trim(), saltBytes, KEYED_ITERATIONS)
    keyMac.mac(stretched)?.let { return PREFIX_KEYED + encode(it) }
    return PREFIX_PBKDF2 + encode(pbkdf2(value.trim(), saltBytes, PBKDF2_ONLY_ITERATIONS))
  }

  /** Timing-safe check of [value] against any supported stored format. */
  fun verify(
    value: String,
    salt: String,
    expectedHash: String,
    keyMac: PinKeyMac = KeystorePinKeyMac,
  ): Boolean {
    if (salt.isBlank() || expectedHash.isBlank()) return false
    return runCatching {
      val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
      val actual: ByteArray =
        when {
          expectedHash.startsWith(PREFIX_KEYED) ->
            keyMac.mac(pbkdf2(value.trim(), saltBytes, KEYED_ITERATIONS)) ?: return false
          expectedHash.startsWith(PREFIX_PBKDF2) -> pbkdf2(value.trim(), saltBytes, PBKDF2_ONLY_ITERATIONS)
          else -> legacySha256(value, saltBytes)
        }
      val expected = Base64.decode(expectedHash.substringAfterLast('$'), Base64.NO_WRAP)
      MessageDigest.isEqual(actual, expected)
    }.getOrDefault(false)
  }

  /** True when [storedHash] should be replaced by a fresh [hash] after a successful check. */
  fun needsUpgrade(storedHash: String): Boolean = !storedHash.startsWith(PREFIX_KEYED)

  private fun legacySha256(
    value: String,
    salt: ByteArray,
  ): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    return digest.digest(value.trim().toByteArray(Charsets.UTF_8))
  }

  private fun pbkdf2(
    value: String,
    salt: ByteArray,
    iterations: Int,
  ): ByteArray {
    val spec = PBEKeySpec(value.toCharArray(), salt, iterations, 256)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
