package com.quantummpv.app.data.jellyfin

import com.quantummpv.app.utils.UrlSanitizer
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class JellyfinClientTest {

    @Test
    fun testUrlSanitizer_removesApiKey() {
        val url = "http://192.168.1.100:8096/Items/123/stream?api_key=my_secret_token"
        val expected = "http://192.168.1.100:8096/Items/123/stream?api_key=***"
        assertEquals(expected, UrlSanitizer.sanitize(url))
    }

    @Test
    fun testUrlSanitizer_removesToken() {
        val url = "http://192.168.1.100:8096/Items/123/stream?Token=my_secret_token"
        val expected = "http://192.168.1.100:8096/Items/123/stream?Token=***"
        assertEquals(expected, UrlSanitizer.sanitize(url))
    }

    @Test
    fun testUrlSanitizer_noTokens() {
        val url = "http://192.168.1.100:8096/Items/123/stream"
        assertEquals(url, UrlSanitizer.sanitize(url))
    }

    @Test
    fun testUrlSanitizer_null() {
        assertNull(UrlSanitizer.sanitize(null))
    }

    @Test
    fun testExceptionSanitization() {
        val message = "Failed to connect to http://server.com/api?api_key=abc&test=1"
        val expected = "Failed to connect to http://server.com/api?api_key=***&test=1"
        assertEquals(expected, UrlSanitizer.sanitizeExceptionMessage(message))
    }
}
