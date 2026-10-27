/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences

import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import com.quantummpv.app.preferences.preference.PreferenceValueType
import com.quantummpv.app.preferences.preference.SecretPreferenceKeys

/**
 * Decides which preferences a settings backup may contain and validates every imported value.
 *
 * A settings file is untrusted input: anyone holding the unlocked phone can import one. The policy
 * therefore only accepts keys the app actually declares, with the type they are declared as, and
 * never touches security state (the Secure Folder PIN, recovery answers, biometric switch) or
 * Keystore-bound secrets.
 */
object SettingsBackupPolicy {
  /** Version 2 files are written with this policy; version-less files come from older builds. */
  const val FORMAT_VERSION = "2"

  private val excludedPrefixes = listOf("secure_folder_")

  private val excludedKeys =
    setOf(
      BrowserPreferences.ONBOARDING_COMPLETED_KEY,
      // Caches and bookkeeping that are only meaningful on the device that wrote them.
      "wyzie_cached_sources_json",
      "ai_last_verified",
      // Records which defaults this install started with; importing it would change another device's.
      PreferenceDefaultsGeneration.KEY,
    )

  /** Name fragments that mark a value as a credential even if it was never wrapped in encryption. */
  private val secretNamePattern = Regex("(^|_)(api_key|apikey|token|password|passwd|secret|pin|hash|salt)(_|$)")

  sealed interface ImportDecision {
    data class Accept(
      val value: Any,
    ) : ImportDecision

    data class Skip(
      val reason: String,
    ) : ImportDecision
  }

  fun isProtectedKey(key: String): Boolean =
    key in excludedKeys ||
      excludedPrefixes.any(key::startsWith) ||
      SecretPreferenceKeys.contains(key) ||
      secretNamePattern.containsMatchIn(key)

  fun typeOf(value: Any?): PreferenceValueType? =
    when (value) {
      is String -> PreferenceValueType.STRING
      is Int -> PreferenceValueType.INT
      is Long -> PreferenceValueType.LONG
      is Float -> PreferenceValueType.FLOAT
      is Boolean -> PreferenceValueType.BOOLEAN
      is Set<*> -> if (value.all { it is String }) PreferenceValueType.STRING_SET else null
      else -> null
    }

  fun isExportable(
    key: String,
    value: Any?,
  ): Boolean {
    if (value == null || isProtectedKey(key)) return false
    if (value is String && NetworkCredentialCipher.isEncryptedValue(value)) return false
    return typeOf(value) != null
  }

  /**
   * @param declaredType type the running app declares for [key], or the type of the value currently
   *   stored under it. Null means the app does not know the key, so it is skipped.
   */
  fun decideImport(
    key: String,
    importedType: PreferenceValueType?,
    rawValue: String,
    declaredType: PreferenceValueType?,
    stringSetSeparator: String,
  ): ImportDecision {
    if (isProtectedKey(key)) return ImportDecision.Skip("protected setting")
    if (declaredType == null) return ImportDecision.Skip("unknown setting")
    if (importedType == null) return ImportDecision.Skip("unsupported type")
    if (importedType != declaredType) return ImportDecision.Skip("type mismatch")
    if (rawValue.length > MAX_VALUE_LENGTH) return ImportDecision.Skip("value too long")

    val parsed: Any? =
      when (declaredType) {
        PreferenceValueType.STRING ->
          rawValue.takeUnless(NetworkCredentialCipher::isEncryptedValue)
        PreferenceValueType.INT -> rawValue.trim().toIntOrNull()
        PreferenceValueType.LONG -> rawValue.trim().toLongOrNull()
        PreferenceValueType.FLOAT -> rawValue.trim().toFloatOrNull()?.takeIf(Float::isFinite)
        PreferenceValueType.BOOLEAN ->
          when (rawValue.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
          }
        PreferenceValueType.STRING_SET ->
          if (rawValue.isEmpty()) emptySet() else rawValue.split(stringSetSeparator).toSet()
      }
    return parsed?.let(ImportDecision::Accept) ?: ImportDecision.Skip("invalid value")
  }

  private const val MAX_VALUE_LENGTH = 256 * 1024
}
