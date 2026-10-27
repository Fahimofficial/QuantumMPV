package com.quantummpv.app.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.quantummpv.app.database.MpvRxDatabase
import com.quantummpv.app.preferences.SettingsBackupPolicy.ImportDecision
import com.quantummpv.app.preferences.preference.AndroidPreferenceStore
import com.quantummpv.app.preferences.preference.PreferenceValueType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * A settings file is untrusted input. Importing one must never replace the Secure Folder PIN or a
 * Keystore secret, and exporting must never write them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SettingsBackupTest {
  private lateinit var context: Context
  private lateinit var database: MpvRxDatabase
  private lateinit var store: AndroidPreferenceStore
  private lateinit var secureFolder: SecureFolderPreferences
  private lateinit var player: PlayerPreferences
  private lateinit var manager: SettingsManager

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    database = Room.inMemoryDatabaseBuilder(context, MpvRxDatabase::class.java).allowMainThreadQueries().build()
    store = AndroidPreferenceStore(context)
    secureFolder = SecureFolderPreferences(store)
    player = PlayerPreferences(store)
    manager =
      SettingsManager(context, store, database) {
        AiPreferences(store)
        SubtitlesPreferences(store)
      }
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun import(xml: String): SettingsManager.ImportStats = runBlocking { manager.readSettingsFromXml(xml.byteInputStream()) }

  private fun settingsFile(vararg prefs: String) =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<mpvRxSettings version="1.4.5"><preferences>
      |${prefs.joinToString("\n")}
      |</preferences></mpvRxSettings>
    """.trimMargin()

  @Test
  fun importCannotReplaceTheSecureFolderPin() {
    secureFolder.setPin("482913")

    val stats =
      import(
        settingsFile(
          """<preference key="secure_folder_pin_hash" type="string" value="attacker-hash" />""",
          """<preference key="secure_folder_pin_salt" type="string" value="attacker-salt" />""",
          """<preference key="secure_folder_biometric_enabled" type="boolean" value="false" />""",
        ),
      )

    assertTrue(secureFolder.verifyPin("482913"))
    assertEquals(0, stats.imported)
    assertEquals(3, stats.skipped)
  }

  @Test
  fun exportNeverContainsSecurityStateOrSecrets() {
    secureFolder.setPin("482913")
    secureFolder.setSecurityQuestion("pet", "rex")
    PreferenceManager.getDefaultSharedPreferences(context).edit()
      .putString("ai_openai_api_key", "mpvrx-credential:v1:abc.def")
      .putString("subdl_api_key", "plaintext-legacy-key")
      .commit()
    player.savePositionOnQuit.set(false)

    val xml = ByteArrayOutputStream().also { runBlocking { manager.writeSettingsToXml(it) } }.toString("UTF-8")

    assertFalse(xml.contains("secure_folder_"))
    assertFalse(xml.contains("api_key"))
    assertFalse(xml.contains("mpvrx-credential"))
    assertTrue(xml.contains("save_position"))
    assertTrue(xml.contains("format=\"${SettingsBackupPolicy.FORMAT_VERSION}\""))
  }

  @Test
  fun declaredSettingsRoundTripAndUnknownOrMistypedOnesAreSkipped() {
    val stats =
      import(
        settingsFile(
          """<preference key="save_position" type="boolean" value="false" />""",
          """<preference key="save_position_typo" type="boolean" value="true" />""",
          """<preference key="player_time_to_disappear" type="string" value="9000" />""",
        ),
      )

    assertFalse(player.savePositionOnQuit.get())
    assertEquals(1, stats.imported)
    assertEquals(2, stats.skipped)
    assertTrue(stats.skippedKeys.any { it.startsWith("save_position_typo") })
  }

  @Test
  fun invalidNumbersAreRejectedInsteadOfBecomingZero() {
    val before = player.playerTimeToDisappear.get()
    val stats = import(settingsFile("""<preference key="player_time_to_disappear" type="int" value="soon" />"""))

    assertEquals(before, player.playerTimeToDisappear.get())
    assertEquals(1, stats.skipped)
  }

  @Test
  fun policyRejectsCiphertextAndNonFiniteValues() {
    assertTrue(
      SettingsBackupPolicy.decideImport("some_text", PreferenceValueType.STRING, "mpvrx-credential:v1:a.b", PreferenceValueType.STRING, "|||")
        is ImportDecision.Skip,
    )
    assertTrue(
      SettingsBackupPolicy.decideImport("some_float", PreferenceValueType.FLOAT, "NaN", PreferenceValueType.FLOAT, "|||") is ImportDecision.Skip,
    )
    assertEquals(
      ImportDecision.Accept(setOf("a", "b")),
      SettingsBackupPolicy.decideImport("some_set", PreferenceValueType.STRING_SET, "a|||b", PreferenceValueType.STRING_SET, "|||"),
    )
    assertTrue(SettingsBackupPolicy.isProtectedKey("wyzie_api_key"))
    assertFalse(SettingsBackupPolicy.isProtectedKey("pinned_folders"))
  }
}
