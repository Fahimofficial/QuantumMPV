import re

def fix_method(filename, method_name):
    with open(filename, "r") as f:
        lines = f.readlines()
        
    inside_target = False
    brace_depth = 0
    for i in range(len(lines)):
        if method_name in lines[i] and "suspend fun" in lines[i]:
            inside_target = True
            brace_depth = lines[i].count("{") - lines[i].count("}")
            continue
            
        if inside_target:
            brace_depth += lines[i].count("{") - lines[i].count("}")
            if lines[i].startswith("  "):
                lines[i] = lines[i][2:] # dedent by 2 spaces exactly
            
            if brace_depth <= 0:
                inside_target = False
                
    with open(filename, "w") as f:
        f.writelines(lines)

fix_method("app/src/main/java/com/quantummpv/app/ui/browser/filesystem/FileSystemBrowserViewModel.kt", "loadCurrentDirectoryInternal")
fix_method("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt", "loadVideosInternal")
