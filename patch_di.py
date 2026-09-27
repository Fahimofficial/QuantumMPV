with open("app/src/main/java/com/quantummpv/app/di/DatabaseModule.kt", "r") as f:
    content = f.read()

daos = """
  single { get<MpvRxDatabase>().audiobookDao() }
  single { get<MpvRxDatabase>().audiobookshelfServerDao() }
  single { get<MpvRxDatabase>().playbackBookmarkDao() }
"""

if "audiobookDao" not in content:
    content = content.replace("single { get<MpvRxDatabase>().ytdlpDownloadJobDao() }", f"single {{ get<MpvRxDatabase>().ytdlpDownloadJobDao() }}{daos}")

with open("app/src/main/java/com/quantummpv/app/di/DatabaseModule.kt", "w") as f:
    f.write(content)
