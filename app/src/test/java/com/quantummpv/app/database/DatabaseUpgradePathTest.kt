package com.quantummpv.app.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.quantummpv.app.database.dao.MediaIndexFtsQuery
import com.quantummpv.app.database.entities.MediaIndexEntity
import com.quantummpv.app.di.DATABASE_MIGRATIONS
import com.quantummpv.app.di.MIGRATION_23_24
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Reproduces real upgrade paths: v1.0/v1.1 shipped database version 23 and preview builds briefly
 * shipped 24. Each test builds that database from the exported schema JSON, stores user data, then
 * opens it with the current Room database, which validates every table against the v25 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DatabaseUpgradePathTest {
  private lateinit var context: Context
  private val dbName = "upgrade-path-test.db"

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(dbName)
  }

  @After
  fun tearDown() {
    context.deleteDatabase(dbName)
  }

  @Test
  fun version23UpgradesToCurrentSchemaAndKeepsResumePositions() {
    createLegacyDatabase(version = 23)

    withCurrentDatabase { db ->
      val state = runBlocking { db.videoDataDao().getVideoDataByTitle("movie.mkv") }
      assertNotNull("resume position must survive the upgrade", state)
      assertEquals(4_200, state!!.lastPosition)
      assertNull(state.newLabelOverride)
      // New tables are usable after the upgrade.
      runBlocking { db.audiobookshelfServerDao().getAllServers().first() }
    }
  }

  @Test
  fun previewVersion24UpgradesToCurrentSchema() {
    createLegacyDatabase(version = 24)

    withCurrentDatabase { db ->
      assertNotNull(runBlocking { db.videoDataDao().getVideoDataByTitle("movie.mkv") })
    }
  }

  @Test
  fun upgradedMediaIndexSearchMatchesByPrefix() {
    createLegacyDatabase(version = 23)

    withCurrentDatabase { db ->
      val dao = db.mediaIndexDao()
      runBlocking {
        dao.upsert(listOf(indexEntry("/storage/emulated/0/Movies/Big Buck Bunny.mkv")))
        val query = MediaIndexFtsQuery.from("bunn")!!
        assertEquals(1, dao.searchMedia(query).first().size)
        dao.upsert(listOf(indexEntry("/storage/emulated/0/Movies/Star-Wars.mkv")))
        assertEquals(1, dao.searchMedia(MediaIndexFtsQuery.from("sta-wa")!!).first().size)
        dao.clearAll()
        assertEquals(0, dao.searchMedia(query).first().size)
      }
    }
  }

  @Test
  fun ftsQueryBuilderProducesPrefixTermsAndIgnoresBlankInput() {
    assertEquals("\"big*\" \"bunny*\"", MediaIndexFtsQuery.from("  big  bunny* "))
    assertEquals("\"Sta*\" \"Wa*\"", MediaIndexFtsQuery.from("Sta-Wa"))
    assertNull(MediaIndexFtsQuery.from("  \"* "))
  }

  private fun withCurrentDatabase(block: (MpvRxDatabase) -> Unit) {
    val db = openCurrent()
    try {
      block(db)
    } finally {
      db.close()
    }
  }

  private fun openCurrent(): MpvRxDatabase =
    Room
      .databaseBuilder(context, MpvRxDatabase::class.java, dbName)
      .addMigrations(*DATABASE_MIGRATIONS)
      .allowMainThreadQueries()
      .build()
      .also { it.openHelper.writableDatabase }

  /** Creates the on-device database exactly as the given exported schema describes it. */
  private fun createLegacyDatabase(version: Int) {
    val schemaVersion = if (version == 24) 23 else version
    val schema = loadSchema(schemaVersion)
    val callback =
      object : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          schema.createStatements().forEach(db::execSQL)
          db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
          db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.identityHash}')",
          )
          // Version 24 preview builds were version 23 plus the 23→24 media-index migration.
          if (version == 24) MIGRATION_23_24.migrate(db)
          db.execSQL(
            "INSERT INTO PlaybackStateEntity (mediaTitle, lastPosition, playbackSpeed, videoZoom, sid, " +
              "secondarySid, subDelay, subSpeed, aid, audioDelay, timeRemaining, externalSubtitles, " +
              "hasBeenWatched) VALUES ('movie.mkv', 4200, 1.0, 0.0, 1, -1, 0, 1.0, 1, 0, 60, '', 0)",
          )
        }

        override fun onUpgrade(
          db: SupportSQLiteDatabase,
          oldVersion: Int,
          newVersion: Int,
        ) = Unit
      }
    val config =
      SupportSQLiteOpenHelper.Configuration
        .builder(context)
        .name(dbName)
        .callback(callback)
        .build()
    FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }
  }

  private class Schema(
    private val database: JsonObject,
  ) {
    val identityHash: String = database.getValue("identityHash").jsonPrimitive.content

    fun createStatements(): List<String> =
      database.getValue("entities").jsonArray.flatMap { entity ->
        val obj = entity.jsonObject
        val table = obj.getValue("tableName").jsonPrimitive.content
        buildList {
          add(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
          obj["indices"]?.jsonArray?.forEach { index ->
            add(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
          }
          obj["contentSyncTriggers"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
        }
      }
  }

  private fun loadSchema(version: Int): Schema {
    val file = File("schemas/com.quantummpv.app.database.MpvRxDatabase/$version.json")
    val root = Json.parseToJsonElement(file.readText()).jsonObject
    return Schema(root.getValue("database").jsonObject)
  }

  private fun indexEntry(path: String) =
    MediaIndexEntity(
      uri = "file://$path",
      path = path,
      parentFolder = File(path).parent.orEmpty(),
      displayName = File(path).name,
      extension = File(path).extension,
      mediaType = 0,
      size = 1L,
      lastModified = 0L,
      durationMs = null,
      hasThumbnail = false,
    )
}
