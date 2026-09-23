package com.quantummpv.app.utils

import android.net.Uri

object UrlSanitizer {
  private const val REDACTED_VALUE = "***"
  private val SENSITIVE_KEYS = setOf("api_key", "ApiKey", "token", "Token", "X-Emby-Token", "api_token", "X-Api-Key", "X-Plex-Token")

  fun sanitize(url: String?): String? {
    if (url.isNullOrBlank()) return url

    return try {
      val uri = Uri.parse(url)
      if (uri.isOpaque) return url

      var sanitizedUri = uri.buildUpon().clearQuery()
      var hasSensitive = false

      for (key in uri.queryParameterNames) {
        if (SENSITIVE_KEYS.any { it.equals(key, ignoreCase = true) }) {
          sanitizedUri.appendQueryParameter(key, REDACTED_VALUE)
          hasSensitive = true
        } else {
          for (value in uri.getQueryParameters(key)) {
            sanitizedUri.appendQueryParameter(key, value)
          }
        }
      }

      if (hasSensitive) {
        sanitizedUri.build().toString()
      } else {
        url
      }
    } catch (e: Exception) {
      url
    }
  }

  fun sanitizeExceptionMessage(message: String?): String? {
    if (message.isNullOrBlank()) return message

    // Look for URL patterns that might contain api_key
    var sanitizedMessage = message
    val urlRegex = Regex("""(http|https)://[^\s"'<>]+""")
    val matches = urlRegex.findAll(message)
    for (match in matches) {
      val url = match.value
      val sanitized = sanitize(url)
      if (sanitized != url && sanitized != null) {
        sanitizedMessage = sanitizedMessage?.replace(url, sanitized)
      }
    }
    return sanitizedMessage
  }
}
