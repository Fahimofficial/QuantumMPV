package com.quantummpv.app.presentation.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RemoteImageDownloadTest {
  @Test
  fun copiesBodiesWithinTheLimit() {
    val body = ByteArray(10_000) { it.toByte() }
    val output = ByteArrayOutputStream()

    assertTrue(RemoteImageLoader.copyBounded(ByteArrayInputStream(body), output, limit = 10_000))
    assertArrayEquals(body, output.toByteArray())
  }

  @Test
  fun rejectsBodiesLargerThanTheLimit() {
    val output = ByteArrayOutputStream()

    assertFalse(RemoteImageLoader.copyBounded(ByteArrayInputStream(ByteArray(10_001)), output, limit = 10_000))
  }
}
