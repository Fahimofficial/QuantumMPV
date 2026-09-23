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

    // Incomplete unicode escape at the end of the string.
    val reallyIncompleteUnicode = "{\"k\":\"\\u\"}"
    val reallyIncompleteUnicodeExpected = "{\"k\":\"\\\\u\"}"
    assertEquals(reallyIncompleteUnicodeExpected, sanitizeJsonString(reallyIncompleteUnicode))
  }

  @Test
  fun sanitizeJsonString_unicodeBoundaryCase() {
    // A unicode escape that is cut off exactly at the boundary (length check)
    // E.g. "\u12" (only 2 hex chars after 'u')
    // Length is evaluated as `i + 5 < jsonString.length`.
    val boundaryUnicode = "{\"k\":\"\\u12\"}"
    // According to the function's logic `char == '\\'` -> `nextChar == 'u'` -> `i + 5 < jsonString.length`.
    // In `"{"k":"\u12"}"`: length = 12.
    // Index of '\' is 6. `6 + 5 = 11`. 11 < 12 is true. So it's considered valid.
    val boundaryUnicodeExpected = "{\"k\":\"\\u12\"}"
    assertEquals(boundaryUnicodeExpected, sanitizeJsonString(boundaryUnicode))

    val boundaryUnicode2 = "{\"k\":\"\\u1\"}"
    // In `"{"k":"\u1"}"`: length = 11.
    // Index of '\' is 6. `6 + 5 = 11`. 11 < 11 is false! So it's considered invalid.
    val boundaryUnicodeExpected2 = "{\"k\":\"\\\\u1\"}"
    assertEquals(boundaryUnicodeExpected2, sanitizeJsonString(boundaryUnicode2))
  }

  @Test
  fun sanitizeJsonString_backslashOutsideStringLiteral_remainsUnchanged() {
    // Though invalid JSON overall, the function's logic only escapes backslashes *inside* string literals.
    val input = """{ \x : "value" }"""
    val expected = """{ \x : "value" }"""
    assertEquals(expected, sanitizeJsonString(input))
  }
}
