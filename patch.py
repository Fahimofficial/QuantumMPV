with open("app/src/main/java/com/quantummpv/app/database/MpvRxDatabase.kt", "r") as f:
    content = f.read()

# Add entities
content = content.replace("YtdlpDownloadJobEntity::class,", "YtdlpDownloadJobEntity::class,\n    com.quantummpv.app.database.entities.AudiobookEntity::class,\n    com.quantummpv.app.database.entities.AudiobookshelfServerEntity::class,\n    com.quantummpv.app.database.entities.PlaybackBookmarkEntity::class,")

# Change version
content = content.replace("version = 24", "version = 25")

# Add DAOs
content = content.replace("abstract fun ytdlpDownloadJobDao(): YtdlpDownloadJobDao", "abstract fun ytdlpDownloadJobDao(): YtdlpDownloadJobDao\n  abstract fun audiobookDao(): com.quantummpv.app.database.dao.AudiobookDao\n  abstract fun audiobookshelfServerDao(): com.quantummpv.app.database.dao.AudiobookshelfServerDao\n  abstract fun playbackBookmarkDao(): com.quantummpv.app.database.dao.PlaybackBookmarkDao")

with open("app/src/main/java/com/quantummpv/app/database/MpvRxDatabase.kt", "w") as f:
    f.write(content)
