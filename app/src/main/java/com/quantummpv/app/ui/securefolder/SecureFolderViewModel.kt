/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.securefolder

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quantummpv.app.R
import com.quantummpv.app.database.entities.SecureMediaEntity
import com.quantummpv.app.database.repository.SecureFolderRepository
import com.quantummpv.app.preferences.SecureFolderCheck
import com.quantummpv.app.preferences.SecureFolderPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * ViewModel driving both the PIN gate and the unlocked grid of [SecureFolderScreen].
 */
class SecureFolderViewModel(
  application: Application,
) : AndroidViewModel(application) {
  val preferences by inject<SecureFolderPreferences>(SecureFolderPreferences::class.java)
  private val repository by inject<SecureFolderRepository>(SecureFolderRepository::class.java)

  companion object {
    private const val TAG = "SecureFolderViewModel"

    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          SecureFolderViewModel(application)
        }
      }
  }

  // ============================================================================
  // Gate state
  // ============================================================================

  enum class GateStep {
    ENTER_PIN,
    SETUP,
    FORGOT_PIN_QUESTION,
    FORGOT_PIN_NEW_PIN,
  }

  private val _gateStep =
    MutableStateFlow(
      if (preferences.isPinSet()) GateStep.ENTER_PIN else GateStep.SETUP
    )
  val gateStep: StateFlow<GateStep> = _gateStep.asStateFlow()

  private val _gateError = MutableStateFlow<String?>(null)
  val gateError: StateFlow<String?> = _gateError.asStateFlow()

  /** Re-evaluates whether the PIN is set, keeping [gateStep] up-to-date across entries. */
  fun refreshGateStep() {
    _gateError.value = null
    _gateStep.value = if (preferences.isPinSet()) GateStep.ENTER_PIN else GateStep.SETUP
  }

  /** Called from ENTER_PIN. On success the caller (Gate screen) navigates to the grid. */
  fun verifyPin(pin: String): Boolean = report(preferences.checkPin(pin), R.string.secure_folder_error_incorrect_pin)

  /** Message for an active lockout, or null when PIN entry is allowed right now. */
  fun lockoutMessage(): String? =
    preferences.lockoutRemainingMillis().takeIf { it > 0L }?.let(::lockoutText)

  private fun report(
    result: SecureFolderCheck,
    @StringRes incorrectMessage: Int,
  ): Boolean {
    val app = getApplication<Application>()
    _gateError.value =
      when (result) {
        SecureFolderCheck.Success -> null
        is SecureFolderCheck.LockedOut -> lockoutText(result.remainingMillis)
        is SecureFolderCheck.Incorrect ->
          if (result.attemptsBeforeLockout in 1..2) {
            app.getString(incorrectMessage) + "\n" +
              app.resources.getQuantityString(
                R.plurals.secure_folder_attempts_left,
                result.attemptsBeforeLockout,
                result.attemptsBeforeLockout,
              )
          } else {
            app.getString(incorrectMessage)
          }
      }
    return result == SecureFolderCheck.Success
  }

  private fun lockoutText(remainingMillis: Long): String {
    val seconds = ((remainingMillis + 999) / 1000).toInt().coerceAtLeast(1)
    val app = getApplication<Application>()
    return if (seconds < 60) {
      app.resources.getQuantityString(R.plurals.secure_folder_locked_seconds, seconds, seconds)
    } else {
      val minutes = (seconds + 59) / 60
      app.resources.getQuantityString(R.plurals.secure_folder_locked_minutes, minutes, minutes)
    }
  }

  /**
   * First-time setup: PIN and security question/answer are validated and persisted together in
   * one atomic call — there's no intermediate "pending PIN" state to lose between steps.
   */
  fun submitSetup(
    pin: String,
    question: String,
    answer: String,
  ): Boolean {
    if (pin.length != 4) {
      _gateError.value = getApplication<Application>().getString(R.string.secure_folder_error_pin_min_digits)
      return false
    }
    preferences.setPin(pin)
    // The recovery question is optional: a blank answer means "recover with the screen lock only".
    if (question.isNotBlank() && answer.isNotBlank()) {
      preferences.setSecurityQuestion(question, answer)
    } else {
      preferences.clearSecurityQuestion()
    }
    _gateError.value = null
    // Keep gateStep consistent with isPinSet() now that a PIN exists, in case this ViewModel
    // instance is revisited later (see refreshGateStep()) instead of being recreated.
    _gateStep.value = GateStep.ENTER_PIN
    return true
  }

  fun startForgotPinFlow() {
    _gateError.value = null
    _gateStep.value = GateStep.FORGOT_PIN_QUESTION
  }

  fun cancelForgotPinFlow() {
    _gateError.value = null
    _gateStep.value = GateStep.ENTER_PIN
  }

  /** Forgot-PIN flow's last step: persists the new PIN directly, no security question re-ask needed. */
  fun finishForgotPinFlow(pin: String): Boolean {
    if (pin.length != 4) {
      _gateError.value = getApplication<Application>().getString(R.string.secure_folder_error_pin_min_digits)
      return false
    }
    preferences.setPin(pin)
    _gateError.value = null
    _gateStep.value = GateStep.ENTER_PIN
    return true
  }

  /** The owner confirmed their device screen lock, which is as strong as the recovery answer. */
  fun recoveredWithScreenLock() {
    preferences.clearFailedAttempts()
    _gateError.value = null
    _gateStep.value = GateStep.FORGOT_PIN_NEW_PIN
  }

  fun verifySecurityAnswerForRecovery(answer: String): Boolean {
    val result = preferences.checkSecurityAnswer(answer)
    val ok = result == SecureFolderCheck.Success
    if (ok) {
      // Don't clear the old PIN here — it stays valid until finishForgotPinFlow() successfully
      // persists the new one via preferences.setPin(), which overwrites the old hash/salt
      // atomically. Clearing it up front would leave isPinSet() == false if the user backs out
      // or the app is killed on the new-PIN screen, letting anyone set a fresh PIN and open the
      // existing Secure Folder contents.
      _gateError.value = null
      _gateStep.value = GateStep.FORGOT_PIN_NEW_PIN
    } else {
      report(result, R.string.secure_folder_error_recovery_no_match)
    }
    return ok
  }

  // ============================================================================
  // Grid state (hidden media)
  // ============================================================================

  val secureMedia: StateFlow<List<SecureMediaEntity>> =
    repository
      .observeAll()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val operationProgress = repository.progress

  private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
  val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

  val isInSelectionMode: StateFlow<Boolean> =
    _selectedIds
      .map { it.isNotEmpty() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _operationResult = MutableStateFlow<String?>(null)
  val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

  private var currentOperationJob: Job? = null

  fun clearOperationResult() {
    _operationResult.value = null
  }

  fun toggleSelection(id: Long) {
    _selectedIds.value =
      if (_selectedIds.value.contains(id)) {
        _selectedIds.value - id
      } else {
        _selectedIds.value + id
      }
  }

  fun handleLongClick(id: Long) {
    toggleSelection(id)
  }

  fun selectAll() {
    _selectedIds.value = secureMedia.value.map { it.id }.toSet()
  }

  fun clearSelection() {
    _selectedIds.value = emptySet()
  }

  fun invertSelection() {
    val all = secureMedia.value.map { it.id }.toSet()
    _selectedIds.value = all - _selectedIds.value
  }

  // ============================================================================
  // Biometric authentication
  // ============================================================================
  fun isBiometricEnabled(): Boolean = preferences.isBiometricEnabled.get()

  fun setBiometricEnabled(enabled: Boolean) {
    preferences.isBiometricEnabled.set(enabled)
  }

  fun verifyBiometricPin(pin: String): Boolean {
    return verifyPin(pin)
  }

  /** Toggles whether the "Secure Folder" entry point is hidden from the Preferences screen. */
  fun toggleEntryPointHidden() {
    preferences.isEntryPointHidden.set(!preferences.isEntryPointHidden.get())
  }

  fun restoreSelected() {
    val ids = _selectedIds.value.toList()
    if (ids.isEmpty() || _isBusy.value) return

    currentOperationJob =
      viewModelScope.launch {
        _isBusy.value = true
        runCatching {
          val entities = repository.getByIds(ids)
          repository.restore(getApplication(), entities)
        }.onSuccess { result ->
          result
            .onSuccess { batch ->
              _operationResult.value =
                if (batch.failedIds.isEmpty()) {
                  getApplication<Application>().getString(R.string.secure_folder_restored_success, batch.succeededIds.size)
                } else {
                  getApplication<Application>().getString(R.string.secure_folder_restored_partial, batch.succeededIds.size, batch.failedIds.size)
                }
            }.onFailure { e ->
              Log.e(TAG, "Restore failed", e)
              _operationResult.value = getApplication<Application>().getString(R.string.secure_folder_restore_failed, e.message ?: "")
            }
        }.onFailure { e ->
          Log.e(TAG, "Restore threw", e)
          _operationResult.value = getApplication<Application>().getString(R.string.secure_folder_restore_failed, e.message ?: "")
        }
        _selectedIds.value = emptySet()
        _isBusy.value = false
      }
  }

  fun deleteSelectedForever() {
    val ids = _selectedIds.value.toList()
    if (ids.isEmpty() || _isBusy.value) return

    currentOperationJob =
      viewModelScope.launch {
        _isBusy.value = true
        runCatching {
          val entities = repository.getByIds(ids)
          repository.deleteForever(entities)
        }.onSuccess { result ->
          result
            .onSuccess { batch ->
              _operationResult.value =
                if (batch.failedIds.isEmpty()) {
                  getApplication<Application>().getString(R.string.secure_folder_deleted_success, batch.succeededIds.size)
                } else {
                  getApplication<Application>().getString(R.string.secure_folder_deleted_partial, batch.succeededIds.size, batch.failedIds.size)
                }
            }.onFailure { e ->
              Log.e(TAG, "Delete failed", e)
              _operationResult.value = getApplication<Application>().getString(R.string.secure_folder_delete_failed, e.message ?: "")
            }
        }.onFailure { e ->
          Log.e(TAG, "Delete threw", e)
          _operationResult.value = getApplication<Application>().getString(R.string.secure_folder_delete_failed, e.message ?: "")
        }
        _selectedIds.value = emptySet()
        _isBusy.value = false
      }
  }

  fun cancelCurrentOperation() {
    repository.cancelOperation()
  }
}
