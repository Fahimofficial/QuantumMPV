import re

with open("app/src/main/java/com/quantummpv/app/ui/browser/folderlist/FolderListScreen.kt", "r") as f:
    content = f.read()

old_build = """  SearchManager.engine.buildIndex(folders, videosByFolder)
}"""

new_build = """  // SearchManager.engine.buildIndex(folders, videosByFolder) is no longer needed with FTS4
}"""

content = content.replace(old_build, new_build)

with open("app/src/main/java/com/quantummpv/app/ui/browser/folderlist/FolderListScreen.kt", "w") as f:
    f.write(content)
