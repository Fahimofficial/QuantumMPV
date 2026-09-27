def fix_lines(filename):
    with open(filename, "r") as f:
        lines = f.readlines()
        
    for i in range(len(lines)):
        # Very simple wrapping for the specific long lines if they exceed 120
        if len(lines[i]) > 120 and "(it.name.endsWith" in lines[i]:
            lines[i] = lines[i].replace("&& (it.name.endsWith(\".part\") || it.name.endsWith(\".ytdl\"))", "\n                && (it.name.endsWith(\".part\") || it.name.endsWith(\".ytdl\"))")
        if len(lines[i]) > 120 and "cleanup assumes ownership" in lines[i]:
            lines[i] = lines[i].replace("// Legacy part file should be deleted (cleanup assumes ownership if legacy part exists, though this edge case deletes all legacy parts for the same title)", "// Legacy part file should be deleted")
        if len(lines[i]) > 120 and "PlayerIntegrationTest.kt" in filename and "EXTRA_STREAM" in lines[i]:
            lines[i] = lines[i].replace("Intent.EXTRA_STREAM", "\n            Intent.EXTRA_STREAM")
            
    with open(filename, "w") as f:
        f.writelines(lines)

fix_lines("app/src/test/java/com/quantummpv/app/domain/download/YtdlpDownloadEngineTest.kt")
fix_lines("app/src/test/java/com/quantummpv/app/ui/player/PlayerIntegrationTest.kt")
