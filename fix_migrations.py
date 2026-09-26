import re

with open("app/src/main/java/com/quantummpv/app/di/DatabaseModule.kt", "r") as f:
    content = f.read()

new_migration = """val MIGRATION_23_24 =
  object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("CREATE TABLE IF NOT EXISTS `media_index` (`uri` TEXT NOT NULL, `path` TEXT NOT NULL, `parentFolder` TEXT NOT NULL, `displayName` TEXT NOT NULL, `extension` TEXT NOT NULL, `mediaType` INTEGER NOT NULL, `size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `durationMs` INTEGER, `hasThumbnail` INTEGER NOT NULL, PRIMARY KEY(`uri`))")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_parentFolder` ON `media_index` (`parentFolder`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_mediaType` ON `media_index` (`mediaType`)")
      db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `media_index_fts` USING FTS4(`displayName`, `path`, content=`media_index`)")
    }
  }

val DatabaseModule ="""

content = content.replace("val DatabaseModule =", new_migration)
content = content.replace("MIGRATION_22_23,", "MIGRATION_22_23,\n          MIGRATION_23_24,")

with open("app/src/main/java/com/quantummpv/app/di/DatabaseModule.kt", "w") as f:
    f.write(content)
