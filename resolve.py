with open("app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt", "r") as f:
    lines = f.readlines()
    
out_lines = []
skip = False
for line in lines:
    if line.startswith("<<<<<<< HEAD"):
        pass
    elif line.startswith("======="):
        skip = True
    elif line.startswith(">>>>>>>"):
        skip = False
    else:
        if not skip:
            out_lines.append(line)

with open("app/src/main/java/com/quantummpv/app/repository/MediaFileRepository.kt", "w") as f:
    f.writelines(out_lines)
