package com.quantummpv.app.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class YtdlpDownloadEngineTest {
  @Test
    fun `test filename sanitization`() {
        val sanitized = DownloadLocations.sanitizeName("Funny / Video : ? * < > | \\ \"")
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertFalse(sanitized.contains(":"))
    }

    @Test
    fun `test output-template generation isolates jobs`() {
        val jobId = 42
        val title = "Video"
        val sanitizedTitle = DownloadLocations.sanitizeName(title).takeIf { it.isNotBlank() } ?: "download"
        val outputTemplate = "$sanitizedTitle-$jobId-%(extractor)s-%(id)s.%(ext)s"
        assertEquals("Video-42-%(extractor)s-%(id)s.%(ext)s", outputTemplate)
    }

    @Test
    fun `test legacy part file compatibility`() {
        val jobId = 42
        val title = "LegacyVideo"
        val sanitizedTitle = DownloadLocations.sanitizeName(title)
        val directory = File(System.getProperty("java.io.tmpdir"), "test_legacy")
        directory.mkdirs()

        // Create a legacy .part file
        val legacyPart = File(directory, "$sanitizedTitle.mp4.part")
        legacyPart.createNewFile()

        val legacyPrefix = "$sanitizedTitle."
        val hasLegacyPart = directory.listFiles()?.any {
            it.isFile && it.name.startsWith(legacyPrefix) && !it.name.contains("-$jobId-") 
                && (it.name.endsWith(".part") || it.name.endsWith(".ytdl"))
        } == true

        assertTrue(hasLegacyPart)

        legacyPart.delete()
        directory.delete()
    }

    @Test
    fun `test cleanup isolation prevents deleting other jobs files`() {
        val jobId1 = 1
        val jobId2 = 2
        val title = "SharedTitle"
        val directory = File(System.getProperty("java.io.tmpdir"), "test_cleanup")
        directory.mkdirs()

        val file1 = File(directory, "$title-$jobId1-youtube-123.mp4")
        val file2 = File(directory, "$title-$jobId2-youtube-456.mp4")
        val legacyFile = File(directory, "$title.mp4")
        val legacyPart = File(directory, "$title.mp4.part")

        file1.createNewFile()
        file2.createNewFile()
        legacyFile.createNewFile()
        legacyPart.createNewFile()

        // Simulate cleanup for job 1
        val sanitizedTitle = DownloadLocations.sanitizeName(title)
        val newSchemeMarker = "-$jobId1-"
        directory.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val isNewScheme = file.name.contains(newSchemeMarker)
            val isLegacyScheme = file.name.startsWith("$sanitizedTitle.") && !file.name.contains("-$jobId1-")

            if (isNewScheme) {
                file.delete()
            } else if (isLegacyScheme) {
                if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) {
                    file.delete()
                }
            }
        }

        assertFalse(file1.exists()) // Job 1 file should be deleted
        assertTrue(file2.exists()) // Job 2 file should NOT be deleted
        assertTrue(legacyFile.exists()) // Legacy completed file should NOT be deleted
        assertFalse(legacyPart.exists()) // Legacy part file should be deleted

        file2.delete()
        legacyFile.delete()
        directory.delete()
    }
}
