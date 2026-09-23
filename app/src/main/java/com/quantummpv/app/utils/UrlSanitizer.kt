package com.quantummpv.app.utils

object UrlSanitizer {
  private const val REDACTED_VALUE = "***"
  private val SENSITIVE_KEYS = setOf("api_key", "token", "x-emby-token", "api_token", "x-api-key", "x-plex-token")

  fun sanitize(url: String?): String? {
    if (url.isNullOrBlank()) return url
    var sanitizedUrl = url
    for (key in SENSITIVE_KEYS) {
        val regex = Regex("""([?&]$key=)([^&#\s"']+)""", RegexOption.IGNORE_CASE)
        sanitizedUrl = regex.replace(sanitizedUrl!!, "$1$REDACTED_VALUE")
    }
    return sanitizedUrl
  }

  fun sanitizeExceptionMessage(message: String?): String? {
    if (message.isNullOrBlank()) return message
    var sanitizedMessage = message
    for (key in SENSITIVE_KEYS) {
        val regex = Regex("""([?&]$key=)([^&#\s"']+)""", RegexOption.IGNORE_CASE)
        sanitizedMessage = regex.replace(sanitizedMessage!!, "$1$REDACTED_VALUE")
    }
    return sanitizedMessage
  }
}
