with open("app/src/main/java/com/quantummpv/app/database/MpvRxDatabase.kt", "r") as f:
    content = f.read()

content = content.replace("com.quantummpv.app.database.entities.AudiobookEntity::class,", "com.quantummpv.app.database.entities.AudiobookEntity::class,\n    com.quantummpv.app.database.entities.AudiobookTrackEntity::class,\n    com.quantummpv.app.database.entities.AudiobookChapterEntity::class,")

with open("app/src/main/java/com/quantummpv/app/database/MpvRxDatabase.kt", "w") as f:
    f.write(content)
