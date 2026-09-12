package com.quantummpv.app.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quantummpv.app.di.MIGRATION_19_20
import com.quantummpv.app.di.MIGRATION_19_21
import com.quantummpv.app.di.MIGRATION_20_21
import com.quantummpv.app.di.MIGRATION_21_22
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MpvRxDatabaseMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    MpvRxDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory(),
  )

  @Test
  fun migrate19To21ThroughSequentialMigrations() {
    helper.createDatabase(TEST_DATABASE, 19).close()
    helper.runMigrationsAndValidate(
      TEST_DATABASE,
      21,
      true,
      MIGRATION_19_20,
      MIGRATION_20_21,
    )
  }

  @Test
  fun migrate19To21ThroughDirectMigration() {
    helper.createDatabase(TEST_DATABASE_DIRECT, 19).close()
    helper.runMigrationsAndValidate(TEST_DATABASE_DIRECT, 21, true, MIGRATION_19_21)
  }

  @Test
  fun migration21To22CreatesDurableYtdlpQueueTable() {
    val database = helper.createDatabase(TEST_DATABASE_YTDLP, 21)
    MIGRATION_21_22.migrate(database)
    database.execSQL(
      "INSERT INTO ytdlp_download_jobs (id, url, title, directory, state, progressPercent, detail, createdAt, updatedAt) " +
        "VALUES (7, 'https://example.com/video', 'Example', '/downloads', 'QUEUED', 0.0, '', 1, 1)",
    )
    database.query("SELECT state, url FROM ytdlp_download_jobs WHERE id = 7").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("QUEUED", cursor.getString(cursor.getColumnIndexOrThrow("state")))
      assertEquals("https://example.com/video", cursor.getString(cursor.getColumnIndexOrThrow("url")))
    }
    database.close()
  }

  @Test
  fun sequentialMigrationCreatesNavidromeDefaultsAndPreservesCredentials() {
    val database = helper.createDatabase(TEST_DATABASE_DEFAULTS, 19)
    MIGRATION_19_20.migrate(database)
    database.execSQL(
      "INSERT INTO navidrome_servers (name, serverUrl, username, password, lastConnected) " +
        "VALUES ('Legacy', 'https://music.example', 'alice', 'secret', 123)",
    )
    MIGRATION_20_21.migrate(database)

    database.query(
      "SELECT token, authMode, username, password, lastConnected " +
        "FROM navidrome_servers WHERE name = 'Legacy'",
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("token")))
      assertEquals("CREDENTIALS", cursor.getString(cursor.getColumnIndexOrThrow("authMode")))
      assertEquals("alice", cursor.getString(cursor.getColumnIndexOrThrow("username")))
      assertEquals("secret", cursor.getString(cursor.getColumnIndexOrThrow("password")))
      assertEquals(123L, cursor.getLong(cursor.getColumnIndexOrThrow("lastConnected")))
    }
    database.close()
  }

  companion object {
    private const val TEST_DATABASE = "migration-sequential.db"
    private const val TEST_DATABASE_DIRECT = "migration-direct.db"
    private const val TEST_DATABASE_DEFAULTS = "migration-defaults.db"
    private const val TEST_DATABASE_YTDLP = "migration-ytdlp.db"
  }
}
