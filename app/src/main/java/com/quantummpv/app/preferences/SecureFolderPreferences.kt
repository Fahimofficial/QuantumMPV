/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.preferences

import com.quantummpv.app.preferences.preference.Preference
import com.quantummpv.app.preferences.preference.PreferenceStore
import com.quantummpv.app.utils.security.KeystorePinKeyMac
import com.quantummpv.app.utils.security.PinHasher
import com.quantummpv.app.utils.security.PinKeyMac

/** Outcome of a PIN or recovery-answer check. */
sealed interface SecureFolderCheck {
  data object Success : SecureFolderCheck

  /** Wrong value. [attemptsBeforeLockout] is 0 when the next failure starts a lockout. */
  data class Incorrect(
    val attemptsBeforeLockout: Int,
  ) : SecureFolderCheck

  /** Too many failures; nothing is checked until [remainingMillis] have passed. */
  data class LockedOut(
    val remainingMillis: Long,
  ) : SecureFolderCheck
}

/**
 * Secure Folder settings: PIN, optional recovery question, biometric and confirmation switches.
 *
 * Kept in their own preferences file ([FILE_NAME]) so backup rules can exclude them, and so a
 * settings import can never reach them. Only salted, device-bound hashes are persisted — never the
 * raw PIN or answer — and every check is rate limited across app restarts.
 */
class SecureFolderPreferences(
  preferenceStore: PreferenceStore,
  legacyStore: PreferenceStore? = null,
  private val keyMac: PinKeyMac = KeystorePinKeyMac,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  // ============================================================================
  // PIN
  // ============================================================================
  private val pinHash = preferenceStore.getString("secure_folder_pin_hash", "")
  private val pinSalt = preferenceStore.getString("secure_folder_pin_salt", "")

  // ============================================================================
  // Forgot-PIN security question (optional)
  // ============================================================================
  val securityQuestion = preferenceStore.getString("secure_folder_security_question", "")
  private val securityAnswerHash = preferenceStore.getString("secure_folder_security_answer_hash", "")
  private val securityAnswerSalt = preferenceStore.getString("secure_folder_security_answer_salt", "")

  // ============================================================================
  // Attempt throttling (shared by PIN and recovery answer)
  // ============================================================================
  private val failedAttempts = preferenceStore.getInt("secure_folder_failed_attempts", 0)
  private val lockoutUntil = preferenceStore.getLong("secure_folder_lockout_until", 0L)

  // ============================================================================
  // Visibility of the Secure Folder entry point
  // ============================================================================

  /** When true, the "Secure Folder" row is hidden from the Preferences screen (still reachable via title double-tap). */
  val isEntryPointHidden = preferenceStore.getBoolean("secure_folder_entry_hidden", false)

  // ============================================================================
  // Biometric authentication
  // ============================================================================
  val isBiometricEnabled = preferenceStore.getBoolean("secure_folder_biometric_enabled", false)

  // ============================================================================
  // Don't-ask-again flags for SecureConfirmDialog
  // ============================================================================
  val dontAskBeforeMove = preferenceStore.getBoolean("secure_folder_dont_ask_move", false)
  val dontAskBeforeRestore = preferenceStore.getBoolean("secure_folder_dont_ask_restore", false)
  val dontAskBeforeDelete = preferenceStore.getBoolean("secure_folder_dont_ask_delete", false)
  val dontAskBeforeHideEntryPoint = preferenceStore.getBoolean("secure_folder_dont_ask_hide_entry_point", false)

  init {
    if (legacyStore != null) migrateFrom(legacyStore)
  }

  fun isPinSet(): Boolean = pinHash.get().isNotBlank()

  /** Hashes and stores a new PIN, replacing any existing one. */
  fun setPin(pin: String) {
    val salt = PinHasher.generateSalt()
    pinSalt.set(salt)
    pinHash.set(PinHasher.hash(pin, salt, keyMac))
  }

  /** Rate-limited PIN check. A correct PIN stored in an older hash format is re-hashed. */
  fun checkPin(pin: String): SecureFolderCheck =
    throttled {
      val ok = PinHasher.verify(pin, pinSalt.get(), pinHash.get(), keyMac)
      if (ok && PinHasher.needsUpgrade(pinHash.get())) setPin(pin)
      ok
    }

  fun verifyPin(pin: String): Boolean = checkPin(pin) == SecureFolderCheck.Success

  fun clearPin() {
    pinHash.delete()
    pinSalt.delete()
  }

  fun isSecurityQuestionSet(): Boolean = securityQuestion.get().isNotBlank() && securityAnswerHash.get().isNotBlank()

  fun setSecurityQuestion(
    question: String,
    answer: String,
  ) {
    val salt = PinHasher.generateSalt()
    securityQuestion.set(question)
    securityAnswerSalt.set(salt)
    securityAnswerHash.set(PinHasher.hash(normalizeAnswer(answer), salt, keyMac))
  }

  fun clearSecurityQuestion() {
    securityQuestion.delete()
    securityAnswerHash.delete()
    securityAnswerSalt.delete()
  }

  /** Rate-limited recovery-answer check, sharing the PIN's attempt counter. */
  fun checkSecurityAnswer(answer: String): SecureFolderCheck =
    throttled {
      if (!isSecurityQuestionSet()) return@throttled false
      val normalized = normalizeAnswer(answer)
      val ok = PinHasher.verify(normalized, securityAnswerSalt.get(), securityAnswerHash.get(), keyMac)
      if (ok && PinHasher.needsUpgrade(securityAnswerHash.get())) setSecurityQuestion(securityQuestion.get(), answer)
      ok
    }

  fun verifySecurityAnswer(answer: String): Boolean = checkSecurityAnswer(answer) == SecureFolderCheck.Success

  /** Resets the attempt counter after the owner proved identity some other way (screen lock). */
  fun clearFailedAttempts() {
    failedAttempts.delete()
    lockoutUntil.delete()
  }

  /** Remaining lockout, or 0 when checks are allowed. */
  fun lockoutRemainingMillis(): Long {
    val until = lockoutUntil.get()
    if (until <= 0L) return 0L
    val now = clock()
    val remaining = until - now
    // A clock moved backwards must not extend (or freeze) the lockout beyond its longest step.
    return remaining.coerceIn(0L, MAX_LOCKOUT_MS)
  }

  private fun normalizeAnswer(answer: String): String = answer.trim().lowercase()

  /** Call after a successful forgot-PIN flow: wipes the PIN so setup runs again, keeps the Q&A. */
  fun resetPinAfterRecovery() {
    clearPin()
  }

  /** Full reset — used for testing/debug or a "reset Secure Folder" action. */
  fun resetAll() {
    clearPin()
    clearSecurityQuestion()
    clearFailedAttempts()
    isEntryPointHidden.delete()
    isBiometricEnabled.delete()
    dontAskBeforeMove.delete()
    dontAskBeforeRestore.delete()
    dontAskBeforeDelete.delete()
    dontAskBeforeHideEntryPoint.delete()
  }

  private inline fun throttled(check: () -> Boolean): SecureFolderCheck {
    lockoutRemainingMillis().takeIf { it > 0L }?.let { return SecureFolderCheck.LockedOut(it) }
    if (check()) {
      clearFailedAttempts()
      return SecureFolderCheck.Success
    }
    val failures = failedAttempts.get() + 1
    failedAttempts.set(failures)
    val lockout = lockoutFor(failures)
    if (lockout > 0L) {
      lockoutUntil.set(clock() + lockout)
      return SecureFolderCheck.LockedOut(lockout)
    }
    return SecureFolderCheck.Incorrect(attemptsBeforeLockout = (FREE_ATTEMPTS - failures).coerceAtLeast(0))
  }

  /** Moves Secure Folder keys out of the general preferences file (one-time, idempotent). */
  private fun migrateFrom(legacy: PreferenceStore) {
    val stored = legacy.getAll()
    if (stored.keys.none { it.startsWith(KEY_PREFIX) }) return
    fun <T> move(
      target: Preference<T>,
      source: Preference<T>,
    ) {
      if (source.isSet()) {
        if (!target.isSet()) target.set(source.get())
        source.delete()
      }
    }
    move(pinHash, legacy.getString("secure_folder_pin_hash", ""))
    move(pinSalt, legacy.getString("secure_folder_pin_salt", ""))
    move(securityQuestion, legacy.getString("secure_folder_security_question", ""))
    move(securityAnswerHash, legacy.getString("secure_folder_security_answer_hash", ""))
    move(securityAnswerSalt, legacy.getString("secure_folder_security_answer_salt", ""))
    move(isEntryPointHidden, legacy.getBoolean("secure_folder_entry_hidden", false))
    move(isBiometricEnabled, legacy.getBoolean("secure_folder_biometric_enabled", false))
    move(dontAskBeforeMove, legacy.getBoolean("secure_folder_dont_ask_move", false))
    move(dontAskBeforeRestore, legacy.getBoolean("secure_folder_dont_ask_restore", false))
    move(dontAskBeforeDelete, legacy.getBoolean("secure_folder_dont_ask_delete", false))
    move(dontAskBeforeHideEntryPoint, legacy.getBoolean("secure_folder_dont_ask_hide_entry_point", false))
  }

  companion object {
    /** SharedPreferences file name; excluded from backup and device transfer. */
    const val FILE_NAME = "secure_folder"
    private const val KEY_PREFIX = "secure_folder_"

    /** Failures allowed before the first lockout. */
    const val FREE_ATTEMPTS = 5
    private const val FIRST_LOCKOUT_MS = 30_000L
    const val MAX_LOCKOUT_MS = 15 * 60_000L

    /** 30 s after the 5th failure, doubling with each further failure, capped at 15 minutes. */
    fun lockoutFor(failures: Int): Long {
      if (failures < FREE_ATTEMPTS) return 0L
      val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
      return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
    }
  }
}
