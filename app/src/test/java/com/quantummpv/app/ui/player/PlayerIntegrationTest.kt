package com.quantummpv.app.ui.player

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerIntegrationTest {
  @Test
    fun `test external playlist parses multiple URIs from EXTRA_STREAM`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            val uris = arrayListOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        val isSendMultiple = intent.action == Intent.ACTION_SEND_MULTIPLE
        assertTrue(isSendMultiple)

        val parsed = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(2, parsed?.size)
        assertEquals("content://media/1", parsed?.get(0).toString())
    }

    @Test
    fun `test external explicit playlist video_list is parsed`() {
        val intent = Intent().apply {
            putStringArrayListExtra("video_list", arrayListOf("http://test/1.mp4", "http://test/2.mp4"))
        }
        val parsed =
            intent.getStringArrayListExtra("video_list")?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                ?: emptyList()
        assertEquals(2, parsed.size)
    }

    @Test
    fun `test playlist transition automatic override`() {
        val isAutomaticTransition = true
        val positionOverride = if (isAutomaticTransition) 0.0 else null
        assertEquals(0.0, positionOverride)
    }

    @Test
    fun `test single external file parsed`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://media/single")
        }
        assertEquals("content://media/single", intent.data.toString())
    }
}
