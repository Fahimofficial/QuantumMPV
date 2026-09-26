import re

with open("app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt", "r") as f:
    content = f.read()

# Add imports
import_str = "import com.quantummpv.app.database.dao.MediaIndexDao\nimport com.quantummpv.app.database.entities.MediaIndexEntity"
content = content.replace("import com.quantummpv.app.database.MpvRxDatabase", import_str + "\nimport com.quantummpv.app.database.MpvRxDatabase")

inject_dao = "  private val database: MpvRxDatabase by inject()\n  private val mediaIndexDao: MediaIndexDao by inject()"
content = content.replace("  private val database: MpvRxDatabase by inject()", inject_dao)

with open("app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt", "w") as f:
    f.write(content)
