package com.quantummpv.app.preferences.preference

import android.content.Context
import com.quantummpv.app.data.network.credentials.NetworkCredentialCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EncryptedStringPreferenceTest {
  private lateinit var store: AndroidPreferenceStore
  private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
  private val cipher = NetworkCredentialCipher { key }

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication()
    context.getSharedPreferences("encrypted-pref-test", Context.MODE_PRIVATE).edit().clear().commit()
    store = AndroidPreferenceStore(context, context.getSharedPreferences("encrypted-pref-test", Context.MODE_PRIVATE))
  }

  @Test
  fun storesCiphertextAndReturnsPlaintext() {
    val raw = store.getString("api_key", "")
    val pref = EncryptedStringPreference(raw, cipher)

    pref.set("sk-secret")

    assertEquals("sk-secret", pref.get())
    assertNotEquals("sk-secret", raw.get())
    assertTrue(cipher.isEncrypted(raw.get()))
  }

  @Test
  fun migratesLegacyPlaintextOnFirstRead() {
    val raw = store.getString("api_key", "")
    raw.set("legacy-plain")
    val pref = EncryptedStringPreference(raw, cipher)

    assertEquals("legacy-plain", pref.get())
    assertTrue("plaintext must be re-encrypted in place", cipher.isEncrypted(raw.get()))
    assertEquals("legacy-plain", pref.get())
  }

  @Test
  fun unreadableCiphertextBehavesLikeSignedOut() {
    val raw = store.getString("api_key", "")
    EncryptedStringPreference(raw, cipher).set("old-device-secret")
    val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    assertEquals("", EncryptedStringPreference(raw, NetworkCredentialCipher { otherKey }).get())
  }

  @Test
  fun emptyValuesStayEmpty() {
    val raw = store.getString("api_key", "")
    val pref = EncryptedStringPreference(raw, cipher)
    pref.set("")
    assertEquals("", raw.get())
    assertEquals("", pref.get())
  }
}
