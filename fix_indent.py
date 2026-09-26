import re

def fix_file(filename):
    with open(filename, "r") as f:
        lines = f.readlines()
        
    inside_target = False
    for i in range(len(lines)):
        if "private suspend fun " in lines[i]:
            inside_target = True
            continue
            
        if inside_target:
            if lines[i].startswith("      "): # 6 spaces
                lines[i] = lines[i][2:] # remove 2 spaces
            if lines[i].startswith("  }"):
                inside_target = False
                
    with open(filename, "w") as f:
        f.writelines(lines)

fix_file("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt")
fix_file("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt")
