package com.quantummpv.app.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quantummpv.app.di.MIGRATION_19_20
import com.quantummpv.app.di.MIGRATION_19_21
import com.quantummpv.app.di.MIGRATION_20_21
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

  companion object {
    private const val TEST_DATABASE = "migration-sequential.db"
    private const val TEST_DATABASE_DIRECT = "migration-direct.db"
  }
}
