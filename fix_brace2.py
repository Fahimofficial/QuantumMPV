with open("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt", "r") as f:
    lines = f.readlines()

for i in range(265, 280):
    if "      } finally {" in lines[i]:
        if lines[i+2].strip() == "}":
            lines[i+2] = "" # Remove the extra brace
        break

with open("app/src/main/java/com/quantummpv/app/ui/browser/videolist/VideoListViewModel.kt", "w") as f:
    f.writelines(lines)
