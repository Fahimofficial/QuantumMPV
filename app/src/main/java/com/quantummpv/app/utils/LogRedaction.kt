/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.utils

/**
 * Returns [uri] without user info, query or fragment, for logging. Media-server stream URLs carry
 * credentials there (Jellyfin `api_key`, Subsonic `t`/`s`, `user:password@host`).
 */
fun redactUriForLog(uri: String?): String {
  if (uri.isNullOrBlank()) return "<none>"
  val withoutQuery = uri.substringBefore('#').substringBefore('?')
  val schemeEnd = withoutQuery.indexOf("://")
  if (schemeEnd < 0) return withoutQuery
  val authorityStart = schemeEnd + 3
  val authorityEnd = withoutQuery.indexOf('/', authorityStart).let { if (it < 0) withoutQuery.length else it }
  val authority = withoutQuery.substring(authorityStart, authorityEnd)
  val host = authority.substringAfterLast('@')
  return withoutQuery.substring(0, authorityStart) + host + withoutQuery.substring(authorityEnd)
}
