package com.quantummpv.app.utils.media

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer

object SubtitleNormalizer {
  /**
   * Normalizes Unicode composition only in visible subtitle cue/dialogue text. Container metadata,
   * ASS style rows, font names, and ASS override tags are preserved byte-for-byte.
   */
  fun normalizeCueTextToNfcIfNeeded(
    bytes: ByteArray,
    extension: String,
  ): ByteArray {
    val decodedString = decodeUtf8Strict(bytes) ?: return bytes
    val normalizedString =
      when (extension.lowercase()) {
        "ass", "ssa" -> normalizeAssDialogueText(decodedString)
        "srt", "vtt" -> normalizePlainSubtitleText(decodedString)
        else -> decodedString
      }

    return if (normalizedString == decodedString) {
      bytes
    } else {
      normalizedString.toByteArray(StandardCharsets.UTF_8)
    }
  }

  private fun decodeUtf8Strict(bytes: ByteArray): String? =
    try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
      decoder.onMalformedInput(CodingErrorAction.REPORT)
      decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
      decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
      null
    } catch (e: Exception) {
      null
    }

  private fun normalizePlainSubtitleText(text: String): String =
    mapLinesPreservingSeparators(text) { line -> normalizeVisibleTextOutsideAngleTags(line) }

  private fun normalizeAssDialogueText(text: String): String {
    var inEventsSection = false
    var eventFields = DEFAULT_ASS_EVENT_FIELDS

    return mapLinesPreservingSeparators(text) { line ->
      val trimmed = line.trimStart()
      when {
        trimmed.startsWith("[") && trimmed.endsWith("]") -> {
          inEventsSection = trimmed.equals("[Events]", ignoreCase = true)
          line
        }
        inEventsSection && trimmed.startsWith("Format:", ignoreCase = true) -> {
          eventFields =
            line.substringAfter(':', "")
              .split(',')
              .map { it.trim() }
              .filter { it.isNotEmpty() }
              .ifEmpty { DEFAULT_ASS_EVENT_FIELDS }
          line
        }
        inEventsSection && isAssEventLine(trimmed) -> normalizeAssEventLine(line, eventFields)
        else -> line
      }
    }
  }

  private inline fun mapLinesPreservingSeparators(
    text: String,
    transform: (String) -> String,
  ): String {
    val output = StringBuilder(text.length)
    var index = 0

    while (index < text.length) {
      val lineStart = index
      while (index < text.length && text[index] != '\r' && text[index] != '\n') {
        index++
      }

      output.append(transform(text.substring(lineStart, index)))

      if (index < text.length) {
        if (text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
          output.append("\r\n")
          index += 2
        } else {
          output.append(text[index])
          index++
        }
      }
    }

    return output.toString()
  }

  private fun isAssEventLine(trimmedLine: String): Boolean =
    trimmedLine.startsWith("Dialogue:", ignoreCase = true) ||
      trimmedLine.startsWith("Comment:", ignoreCase = true)

  private fun normalizeAssEventLine(
    line: String,
    eventFields: List<String>,
  ): String {
    val textIndex = eventFields.indexOfFirst { it.equals("Text", ignoreCase = true) }
    if (textIndex < 0) return line

    val colonIndex = line.indexOf(':')
    if (colonIndex < 0) return line

    val payload = line.substring(colonIndex + 1)
    val parts = payload.split(',', limit = eventFields.size)
    if (parts.size <= textIndex) return line

    val normalizedText = normalizeAssVisibleText(parts[textIndex])
    if (normalizedText == parts[textIndex]) return line

    val updatedParts = parts.toMutableList()
    updatedParts[textIndex] = normalizedText
    return line.substring(0, colonIndex + 1) + updatedParts.joinToString(",")
  }

  private fun normalizeAssVisibleText(text: String): String {
    val output = StringBuilder(text.length)
    val visibleText = StringBuilder()
    var inOverrideTag = false

    fun flushVisibleText() {
      if (visibleText.isEmpty()) return
      output.append(visibleText.toString().normalizeToNfcIfNeeded())
      visibleText.clear()
    }

    text.forEach { char ->
      when {
        char == '{' && !inOverrideTag -> {
          flushVisibleText()
          inOverrideTag = true
          output.append(char)
        }
        char == '}' && inOverrideTag -> {
          inOverrideTag = false
          output.append(char)
        }
        inOverrideTag -> output.append(char)
        else -> visibleText.append(char)
      }
    }

    flushVisibleText()
    return output.toString()
  }

  private fun normalizeVisibleTextOutsideAngleTags(text: String): String {
    val output = StringBuilder(text.length)
    val visibleText = StringBuilder()
    var inTag = false

    fun flushVisibleText() {
      if (visibleText.isEmpty()) return
      output.append(visibleText.toString().normalizeToNfcIfNeeded())
      visibleText.clear()
    }

    text.forEach { char ->
      when {
        char == '<' && !inTag -> {
          flushVisibleText()
          inTag = true
          output.append(char)
        }
        char == '>' && inTag -> {
          inTag = false
          output.append(char)
        }
        inTag -> output.append(char)
        else -> visibleText.append(char)
      }
    }

    flushVisibleText()
    return output.toString()
  }

  private fun String.normalizeToNfcIfNeeded(): String =
    if (Normalizer.isNormalized(this, Normalizer.Form.NFC)) this else Normalizer.normalize(this, Normalizer.Form.NFC)

  private val DEFAULT_ASS_EVENT_FIELDS =
    listOf("Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text")
}
