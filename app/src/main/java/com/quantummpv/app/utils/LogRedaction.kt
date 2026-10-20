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
  val schemeEnd = uri.indexOf("://")
  if (schemeEnd < 0) return uri.stripQueryAndFragment()
  // Find the authority on the original string first: a malformed password may contain '?' or '#',
  // and stripping those first would leave part of the user info looking like the host.
  val authorityStart = schemeEnd + 3
  val pathStart = uri.indexOf('/', authorityStart).let { if (it < 0) uri.length else it }
  val userInfoEnd = uri.lastIndexOf('@', pathStart - 1)
  val hostStart = if (userInfoEnd >= authorityStart) userInfoEnd + 1 else authorityStart
  val host = uri.substring(hostStart, pathStart).stripQueryAndFragment()
  val path = if (host.length < pathStart - hostStart) "" else uri.substring(pathStart).stripQueryAndFragment()
  return uri.substring(0, authorityStart) + host + path
}

private fun String.stripQueryAndFragment(): String = substringBefore('#').substringBefore('?')
