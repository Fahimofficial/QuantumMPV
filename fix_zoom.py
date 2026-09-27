with open("app/src/main/java/com/quantummpv/app/ui/player/controls/components/sheets/VideoZoomSheet.kt", "r") as f:
    lines = f.readlines()

with open("app/src/main/java/com/quantummpv/app/ui/player/controls/components/sheets/VideoZoomSheet.kt", "w") as f:
    for i, line in enumerate(lines):
        if i == 11 and "package" in line:
            continue
        f.write(line)
