/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.ui.player

import org.junit.Test
import kotlin.test.assertEquals

class PlayerUtilsTest {

  @Test
  fun sanitizeJsonString_validJsonWithoutBackslashes_remainsUnchanged() {
    val input = """{"key": "value", "number": 123}"""
    val expected = """{"key": "value", "number": 123}"""
    assertEquals(expected, sanitizeJsonString(input))
  }

  @Test
  fun sanitizeJsonString_validEscapeSequences_remainsUnchanged() {
    val inputEscapes = "{\"key\": \"\\\"\", \"slash\": \"\\\\/\", \"others\": \"\\b\\f\\n\\r\\t\"}"
    val expectedEscapes = "{\"key\": \"\\\"\", \"slash\": \"\\\\/\", \"others\": \"\\b\\f\\n\\r\\t\"}"
    assertEquals(expectedEscapes, sanitizeJsonString(inputEscapes))

    // Test double backslash behavior as currently implemented (it escapes the second backslash's lack of escape sequence)
    val inputDouble = "{\"path\": \"c:\\\\path\"}"
    val expectedDouble = "{\"path\": \"c:\\\\\\path\"}"
    assertEquals(expectedDouble, sanitizeJsonString(inputDouble))
  }

  @Test
  fun sanitizeJsonString_invalidEscapeSequences_escapesBackslash() {
    // \x is invalid in JSON, should become \\x
    val input = "{\"key\": \"invalid \\x escape\", \"other\": \"invalid \\y escape\"}"
    val expected = "{\"key\": \"invalid \\\\x escape\", \"other\": \"invalid \\\\y escape\"}"
    assertEquals(expected, sanitizeJsonString(input))
  }

  @Test
  fun sanitizeJsonString_unicodeEscapeSequences() {
    // Valid 4-hex digit unicode escape \u1234
    val validUnicode = "{\"key\": \"valid \\u1234 unicode\"}"
    val validUnicodeExpected = "{\"key\": \"valid \\u1234 unicode\"}"
    assertEquals(validUnicodeExpected, sanitizeJsonString(validUnicode))

    // Incomplete unicode escape at the end of the string, e.g., \u123 (length < 5 after \)
    // Note: The function checks `i + 5 < jsonString.length`, meaning after `\`, it checks if there's an `u` + 4 more chars.
    // However, if the entire `jsonString` ends with `\u123"}` it evaluates `i + 5 < jsonString.length`.
    // Wait, `"{"key": "\u123"}"` length is 18.
    // `\` is at index 10. `10 + 5 = 15`. `15 < 18` is true. So it treats it as valid.
    // To make it incomplete, we need `\u` to be at the very end of the string:
    val incompleteUnicode = "{\"key\": \"\\u12\"}" // len = 17. \ at 10. 10+5=15 < 17 is TRUE.

    val reallyIncompleteUnicode = "{\"k\":\"\\u\"}"
    // len: {"k":"\u"} -> 1+3+2+1+1+1+2 = 11? `{"k":"\u"}`
    // `\` is at index 7. `7 + 5 = 12`. 12 < 11 is FALSE.
    val reallyIncompleteUnicodeExpected = "{\"k\":\"\\\\u\"}"
    assertEquals(reallyIncompleteUnicodeExpected, sanitizeJsonString(reallyIncompleteUnicode))
  }

  @Test
  fun sanitizeJsonString_backslashOutsideStringLiteral_remainsUnchanged() {
    // Though invalid JSON overall, the function's logic only escapes backslashes *inside* string literals.
    val input = """{ \x : "value" }"""
    val expected = """{ \x : "value" }"""
    assertEquals(expected, sanitizeJsonString(input))
  }
}
