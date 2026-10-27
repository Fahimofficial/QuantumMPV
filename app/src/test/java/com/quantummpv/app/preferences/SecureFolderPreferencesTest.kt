package com.quantummpv.app.preferences

import android.content.Context
import android.util.Base64
import com.quantummpv.app.preferences.preference.AndroidPreferenceStore
import com.quantummpv.app.utils.security.PinHasher
import com.quantummpv.app.utils.security.PinKeyMac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SecureFolderPreferencesTest {
  private lateinit var context: Context
  private var now = 1_000_000L

  /** Stands in for the Keystore key, which Robolectric does not provide. */
  private val deviceKey =
    PinKeyMac { data ->
      Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256"))
        doFinal(data)
      }
    }

  private fun store(name: String) =
    AndroidPreferenceStore(context, context.getSharedPreferences(name, Context.MODE_PRIVATE))

  private fun preferences(
    legacy: AndroidPreferenceStore? = null,
    keyMac: PinKeyMac = deviceKey,
  ) = SecureFolderPreferences(store("secure_folder"), legacy, keyMac) { now }

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    listOf("secure_folder", "legacy").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
  }

  @Test
  fun correctPinUnlocksAndStoredHashIsDeviceBound() {
    val prefs = preferences()
    prefs.setPin("4821")

    assertEquals(SecureFolderCheck.Success, prefs.checkPin("4821"))
    val stored = context.getSharedPreferences("secure_folder", Context.MODE_PRIVATE).getString("secure_folder_pin_hash", "")!!
    assertTrue(stored.startsWith("v2\$km\$"))
    assertFalse(PinHasher.needsUpgrade(stored))

    // The same preferences file on another device (different key) does not verify.
    val otherDevice = preferences(keyMac = { data -> MessageDigest.getInstance("SHA-256").digest(data) })
    assertFalse(otherDevice.verifyPin("4821"))
  }

  @Test
  fun repeatedFailuresLockOutAndTheLockoutGrows() {
    val prefs = preferences()
    prefs.setPin("4821")

    repeat(SecureFolderPreferences.FREE_ATTEMPTS - 1) { attempt ->
      val result = prefs.checkPin("0000")
      assertTrue(result is SecureFolderCheck.Incorrect)
      assertEquals(SecureFolderPreferences.FREE_ATTEMPTS - attempt - 1, (result as SecureFolderCheck.Incorrect).attemptsBeforeLockout)
    }
    val locked = prefs.checkPin("0000")
    assertEquals(SecureFolderCheck.LockedOut(30_000L), locked)

    // While locked even the correct PIN is not checked.
    now += 10_000L
    assertTrue(prefs.checkPin("4821") is SecureFolderCheck.LockedOut)

    now += 21_000L
    assertEquals(SecureFolderCheck.LockedOut(60_000L), prefs.checkPin("1111"))
    now += 61_000L
    assertEquals(SecureFolderCheck.Success, prefs.checkPin("4821"))
    assertEquals(0L, prefs.lockoutRemainingMillis())
  }

  @Test
  fun lockoutSurvivesRestartsAndIsCapped() {
    preferences().apply {
      setPin("4821")
      repeat(SecureFolderPreferences.FREE_ATTEMPTS) { checkPin("0000") }
    }
    assertTrue("a new instance (app restart) keeps the lockout", preferences().lockoutRemainingMillis() > 0L)

    now -= 10 * 60 * 60_000L // clock moved back ten hours
    assertTrue(preferences().lockoutRemainingMillis() <= SecureFolderPreferences.MAX_LOCKOUT_MS)
    assertEquals(SecureFolderPreferences.MAX_LOCKOUT_MS, SecureFolderPreferences.lockoutFor(50))
  }

  @Test
  fun legacySha256PinStillWorksAndIsUpgradedOnUnlock() {
    val salt = PinHasher.generateSalt()
    val legacyHash =
      Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").run {
          update(Base64.decode(salt, Base64.NO_WRAP))
          digest("4821".toByteArray())
        },
        Base64.NO_WRAP,
      )
    val legacy = store("legacy")
    legacy.getString("secure_folder_pin_salt").set(salt)
    legacy.getString("secure_folder_pin_hash").set(legacyHash)
    legacy.getBoolean("secure_folder_biometric_enabled").set(true)
    legacy.getString("unrelated_setting").set("kept")

    val prefs = preferences(legacy)

    assertTrue("keys moved out of the general preferences file", legacy.getAll().keys.none { it.startsWith("secure_folder_") })
    assertEquals("kept", legacy.getString("unrelated_setting").get())
    assertTrue(prefs.isBiometricEnabled.get())
    assertTrue(prefs.verifyPin("4821"))
    val upgraded = context.getSharedPreferences("secure_folder", Context.MODE_PRIVATE).getString("secure_folder_pin_hash", "")!!
    assertTrue(upgraded.startsWith("v2\$km\$"))
    assertTrue(prefs.verifyPin("4821"))
  }

  @Test
  fun recoveryAnswerIsOptionalAndThrottledWithThePin() {
    val prefs = preferences()
    prefs.setPin("4821")
    assertFalse(prefs.isSecurityQuestionSet())
    assertTrue(prefs.checkSecurityAnswer("anything") is SecureFolderCheck.Incorrect)

    prefs.setSecurityQuestion("City?", "  Lisbon ")
    assertEquals(SecureFolderCheck.Success, prefs.checkSecurityAnswer("lisbon"))
    repeat(SecureFolderPreferences.FREE_ATTEMPTS) { prefs.checkSecurityAnswer("porto") }
    assertTrue(prefs.checkPin("4821") is SecureFolderCheck.LockedOut)

    prefs.clearFailedAttempts()
    assertEquals(SecureFolderCheck.Success, prefs.checkPin("4821"))
  }

  @Test
  fun withoutADeviceKeyAPbkdf2HashIsStoredAndUpgradedLater() {
    val noKeystore = preferences(keyMac = { null })
    noKeystore.setPin("4821")
    val stored = context.getSharedPreferences("secure_folder", Context.MODE_PRIVATE).getString("secure_folder_pin_hash", "")!!
    assertTrue(stored.startsWith("v2\$pb\$"))
    assertTrue(noKeystore.verifyPin("4821"))

    // Once the Keystore is available again, the next unlock moves to the device-bound format.
    assertTrue(preferences().verifyPin("4821"))
    assertTrue(context.getSharedPreferences("secure_folder", Context.MODE_PRIVATE).getString("secure_folder_pin_hash", "")!!.startsWith("v2\$km\$"))
  }
}
