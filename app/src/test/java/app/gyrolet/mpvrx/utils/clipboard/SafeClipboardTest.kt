package com.quantummpv.app.utils.clipboard

import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeClipboardTest {
  @Test
  fun `truncateUtf8 leaves small text intact`() {
    val result = SafeClipboard.truncateUtf8("hello", maxBytes = 32)

    assertFalse(result.truncated)
    assertTrue(result.text == "hello")
  }

  @Test
  fun `truncateUtf8 caps large utf8 text without splitting code points`() {
    val text = "🙂".repeat(100)
    val result = SafeClipboard.truncateUtf8(text, maxBytes = 256)

    assertTrue(result.truncated)
    assertTrue(result.text.toByteArray(StandardCharsets.UTF_8).size <= 256)
    assertTrue(result.text.contains("MPVRX"))
    assertFalse(result.text.contains("\uFFFD"))
  }
}
