with open("app/src/main/java/com/quantummpv/app/utils/history/RecentlyPlayedOps.kt", "r") as f:
    content = f.read()

if "import com.quantummpv.app.domain.media.model.Video" not in content:
    content = content.replace("import java.io.File", "import java.io.File\nimport com.quantummpv.app.domain.media.model.Video")

with open("app/src/main/java/com/quantummpv/app/utils/history/RecentlyPlayedOps.kt", "w") as f:
    f.write(content)
