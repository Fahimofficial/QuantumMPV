/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.quantummpv.app.database.dao

/** Builds MATCH expressions for [MediaIndexDao.searchMedia]. */
object MediaIndexFtsQuery {
  /**
   * Converts free text into an FTS4 MATCH expression. FTS4 has no leading wildcard, so each
   * letter/digit run becomes a quoted prefix term (`"tok*"`); punctuation, quotes and operators
   * in user text are dropped so they cannot break the expression. Returns null when the text has no searchable token.
   */
  fun from(text: String): String? =
    text
      // FTS4's tokenizer splits on punctuation too ("Star-Wars" is two tokens), so do the same
      // here; otherwise "sta-wa" would require "sta" to match a whole token.
      .split(Regex("[^\\p{L}\\p{N}]+"))
      .filter { it.isNotEmpty() }
      .takeIf { it.isNotEmpty() }
      ?.joinToString(" ") { "\"$it*\"" }
}
