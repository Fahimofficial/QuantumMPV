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
   * token becomes a quoted prefix term (`"tok*"`); quotes in user text are stripped so they
   * cannot break the expression. Returns null when the text has no searchable token.
   */
  fun from(text: String): String? =
    text
      .split(Regex("\\s+"))
      .map { token -> token.replace("\"", "").replace("*", "").trim() }
      .filter { it.isNotEmpty() }
      .takeIf { it.isNotEmpty() }
      ?.joinToString(" ") { "\"$it*\"" }
}
