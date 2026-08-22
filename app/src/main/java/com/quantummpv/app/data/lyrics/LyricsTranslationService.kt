/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.data.lyrics

import android.os.Build
import android.util.Log
import android.util.LruCache
import com.quantummpv.app.domain.lyrics.Lyrics
import com.quantummpv.app.domain.lyrics.SyncedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class TranslationResult(
  val translation: String,
  val romanization: String? = null,
  val detectedSourceLang: String? = null,
)

data class SupportedLanguage(
  val code: String,
  val displayName: String,
  val isRomanization: Boolean = false,
)

object LyricsLanguageOptions {
  val ALL_LANGUAGES = listOf(
    SupportedLanguage("en", "English"),
    SupportedLanguage("romaji", "Romaji / Romanized (Hinglish, Pinyin)", isRomanization = true),
    SupportedLanguage("hi", "Hindi (हिन्दी)"),
    SupportedLanguage("es", "Spanish (Español)"),
    SupportedLanguage("fr", "French (Français)"),
    SupportedLanguage("de", "German (Deutsch)"),
    SupportedLanguage("ja", "Japanese (日本語)"),
    SupportedLanguage("ko", "Korean (한국어)"),
    SupportedLanguage("zh-CN", "Chinese (Simplified)"),
    SupportedLanguage("it", "Italian (Italiano)"),
    SupportedLanguage("pt", "Portuguese (Português)"),
    SupportedLanguage("ru", "Russian (Русский)"),
    SupportedLanguage("ar", "Arabic (العربية)"),
    SupportedLanguage("bn", "Bengali (বাংলা)"),
    SupportedLanguage("ta", "Tamil (தமிழ்)"),
    SupportedLanguage("te", "Telugu (తెలుగు)"),
    SupportedLanguage("mr", "Marathi (मराठी)"),
    SupportedLanguage("pa", "Punjabi (ਪੰਜਾਬੀ)"),
    SupportedLanguage("ur", "Urdu (اردو)"),
  )

  fun getDisplayName(code: String): String {
    if (code.equals("hinglish", ignoreCase = true)) return "Romaji / Romanized"
    return ALL_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.displayName ?: code.uppercase()
  }
}

class LyricsTranslationService(
  okHttpClient: OkHttpClient,
) {
  companion object {
    private const val TAG = "LyricsTranslationService"
    private const val CHUNK_SIZE = 25
    private const val USER_AGENT =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val RESULT_CONTAINER_REGEX =
      Regex("""<div[^>]*class=["']result-container["'][^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val BRACKET_PATTERN =
      Regex("""\[\s*(\d+)\s*\]\s*(.*?)(?=\[\s*\d+\s*\]|$)""", RegexOption.DOT_MATCHES_ALL)
    private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?[0-9a-fA-F]+);""")
  }

  // Fast timeout for translation calls (5s connect / 8s read)
  private val client = okHttpClient.newBuilder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  // Cache key: "${mediaPathOrTrackId}_${targetLang}" -> Translated Lyrics
  private val translationCache = LruCache<String, Lyrics>(64)

  suspend fun translateLyrics(
    lyrics: Lyrics,
    targetLanguage: String,
    cacheKey: String? = null,
  ): Lyrics = withContext(Dispatchers.IO) {
    if (!lyrics.isValid()) return@withContext lyrics

    val key = cacheKey?.let { "${it}_$targetLanguage" }
    if (key != null) {
      translationCache.get(key)?.let { return@withContext it }
    }

    try {
      if (!lyrics.synced.isNullOrEmpty()) {
        val translatedSynced = translateSyncedLines(lyrics.synced, targetLanguage)
        val result = lyrics.copy(synced = translatedSynced)
        if (key != null) translationCache.put(key, result)
        return@withContext result
      } else if (!lyrics.plain.isNullOrEmpty()) {
        val translatedPlain = translatePlainLines(lyrics.plain, targetLanguage)
        val result = lyrics.copy(plain = translatedPlain)
        if (key != null) translationCache.put(key, result)
        return@withContext result
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error translating lyrics to $targetLanguage: ${e.message}", e)
    }

    lyrics
  }

  private suspend fun translateSyncedLines(
    lines: List<SyncedLine>,
    targetLanguage: String,
  ): List<SyncedLine> {
    val textsToTranslate = lines.map { it.line }
    val translations = batchTranslate(textsToTranslate, targetLanguage)

    return lines.mapIndexed { index, line ->
      val res = translations.getOrNull(index)
      if (res != null) {
        val translatedText = res.translation.takeIf { it.isNotBlank() }
        val romanizedText = res.romanization?.takeIf { it.isNotBlank() }
        line.copy(
          translation = when {
            targetLanguage == "romaji" || targetLanguage == "hinglish" -> romanizedText ?: translatedText
            else -> translatedText
          },
          romanization = romanizedText,
        )
      } else {
        line
      }
    }
  }

  private suspend fun translatePlainLines(
    lines: List<String>,
    targetLanguage: String,
  ): List<String> {
    val translations = batchTranslate(lines, targetLanguage)
    return lines.mapIndexed { index, original ->
      val res = translations.getOrNull(index)
      if (res != null) {
        val text = if (targetLanguage == "romaji" || targetLanguage == "hinglish") {
          res.romanization ?: res.translation
        } else {
          res.translation
        }
        if (text.isNotBlank() && !text.equals(original.trim(), ignoreCase = true)) {
          "$original\n$text"
        } else {
          original
        }
      } else {
        original
      }
    }
  }

  private suspend fun batchTranslate(
    texts: List<String>,
    targetLang: String,
  ): List<TranslationResult> = coroutineScope {
    if (texts.isEmpty()) return@coroutineScope emptyList()

    // Special handling for Romaji / Hinglish / Romanized
    if (targetLang.equals("romaji", ignoreCase = true) || targetLang.equals("hinglish", ignoreCase = true)) {
      return@coroutineScope handleRomajiTransliteration(texts)
    }

    val chunks = texts.chunked(CHUNK_SIZE)
    val deferredChunks = chunks.map { chunk ->
      async(Dispatchers.IO) {
        translateChunk(chunk, targetLang)
      }
    }

    deferredChunks.awaitAll().flatten()
  }

  /**
   * Handles Romaji / Hinglish / Romanized pronunciation requests.
   * If the text is already in Latin script (e.g. Hinglish or Romaji), keeps the original pronunciation.
   * If the text is in non-Latin script (Devanagari, Kana/Kanji, Hangul, Cyrillic, etc.),
   * fetches accurate Romanization (Romaji for Japanese, Pinyin for Chinese, etc.).
   */
  private suspend fun handleRomajiTransliteration(texts: List<String>): List<TranslationResult> = coroutineScope {
    val isAlreadyLatin = isPredominantlyLatin(texts)
    if (isAlreadyLatin) {
      // Already written in Romaji / Hinglish / Latin script!
      return@coroutineScope texts.map { line ->
        val trimmed = line.trim()
        TranslationResult(
          translation = trimmed,
          romanization = trimmed,
        )
      }
    }

    // Non-Latin script: fetch authentic Romanization (Romaji for Japanese, Pinyin for Chinese, etc.)
    val chunks = texts.chunked(CHUNK_SIZE)
    val deferredChunks = chunks.map { chunk ->
      async(Dispatchers.IO) {
        romanizeChunk(chunk)
      }
    }

    deferredChunks.awaitAll().flatten()
  }

  private fun isPredominantlyLatin(texts: List<String>): Boolean {
    var latinCount = 0
    var nonLatinLetterCount = 0
    for (text in texts) {
      for (c in text) {
        if (Character.isLetter(c)) {
          val block = Character.UnicodeBlock.of(c)
          if (block == Character.UnicodeBlock.BASIC_LATIN ||
            block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_B ||
            block == Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL
          ) {
            latinCount++
          } else {
            nonLatinLetterCount++
          }
        }
      }
    }
    val total = latinCount + nonLatinLetterCount
    if (total == 0) return true
    return (latinCount.toFloat() / total) >= 0.60f
  }

  private fun romanizeChunk(chunk: List<String>): List<TranslationResult> {
    val stringBuilder = StringBuilder()
    val indexMap = mutableMapOf<Int, Int>()
    var marker = 1

    for ((localIdx, line) in chunk.withIndex()) {
      val trimmed = line.trim()
      if (trimmed.isNotEmpty()) {
        stringBuilder.append("[$marker] ").append(trimmed).append("\n")
        indexMap[marker] = localIdx
        marker++
      }
    }

    if (indexMap.isEmpty()) {
      return chunk.map { TranslationResult(translation = "") }
    }

    val queryText = stringBuilder.toString().trim()

    // 1. Primary: Fetch Google Romanization using client=it & dt=rm (authentic Japanese Romaji, Korean Romaja, etc.)
    try {
      val googleRom = fetchGoogleRomanization(queryText)
      if (!googleRom.isNullOrBlank()) {
        val parsed = parseIndexedTranslations(googleRom, chunk.size, indexMap, chunk)
        if (parsed.any { it.translation.isNotBlank() }) {
          return parsed
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Google romanization error: ${e.message}")
    }

    // 2. Fallback: ICU transliteration for non-Kanji scripts
    return chunk.map { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        TranslationResult(translation = "")
      } else {
        val rom = transliterateToLatin(trimmed)
        TranslationResult(translation = rom, romanization = rom)
      }
    }
  }

  private fun fetchGoogleRomanization(query: String): String? {
    val url = HttpUrl.Builder()
      .scheme("https")
      .host("translate.google.com")
      .addPathSegment("translate_a")
      .addPathSegment("single")
      .addQueryParameter("client", "it")
      .addQueryParameter("sl", "auto")
      .addQueryParameter("tl", "en")
      .addQueryParameter("dt", "t")
      .addQueryParameter("dt", "rm")
      .addQueryParameter("q", query)
      .build()

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .get()
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val bodyStr = response.body.string()
      val root = json.parseToJsonElement(bodyStr).jsonArray
      val sentences = root.getOrNull(0)?.jsonArray ?: return null

      // Check sentences array for the full romanization block (in the last element)
      for (i in sentences.indices.reversed()) {
        val elem = sentences.getOrNull(i)?.jsonArray
        val candidate = elem?.getOrNull(3)?.jsonPrimitive?.content
          ?: elem?.getOrNull(2)?.jsonPrimitive?.content
        if (!candidate.isNullOrBlank() && candidate != "null") {
          return candidate.trim()
        }
      }

      val sb = StringBuilder()
      for (elem in sentences) {
        val sub = elem.jsonArray
        val rom = sub.getOrNull(3)?.jsonPrimitive?.content
          ?: sub.getOrNull(2)?.jsonPrimitive?.content
        if (!rom.isNullOrBlank() && rom != "null") {
          sb.append(rom).append(" ")
        }
      }
      return sb.toString().trim().ifEmpty { null }
    }
  }

  private fun transliterateToLatin(text: String): String {
    if (text.isBlank()) return text
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val transliterator = android.icu.text.Transliterator.getInstance("Any-Latin")
        val result = transliterator.transliterate(text)
        if (!result.isNullOrBlank()) return result.trim()
      } catch (e: Throwable) {
        Log.w(TAG, "ICU transliteration error: ${e.message}")
      }
    }
    return text
  }

  private fun translateChunk(
    chunk: List<String>,
    targetLang: String,
  ): List<TranslationResult> {
    val stringBuilder = StringBuilder()
    val indexMap = mutableMapOf<Int, Int>() // marker (1-based) -> index in chunk
    var marker = 1

    for ((localIdx, line) in chunk.withIndex()) {
      val trimmed = line.trim()
      if (trimmed.isNotEmpty()) {
        stringBuilder.append("[$marker] ").append(trimmed).append("\n")
        indexMap[marker] = localIdx
        marker++
      }
    }

    // If entire chunk is blank lines
    if (indexMap.isEmpty()) {
      return chunk.map { TranslationResult(translation = "") }
    }

    val queryText = stringBuilder.toString().trim()

    // 1. Primary engine: Google Mobile Web (Fast, robust, translates multiline chunks in <500ms)
    try {
      val googleResult = translateWithGoogleWeb(queryText, targetLang)
      if (!googleResult.isNullOrBlank()) {
        val parsed = parseIndexedTranslations(googleResult, chunk.size, indexMap, chunk)
        if (parsed.any { it.translation.isNotBlank() }) {
          return parsed
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Google web translation failed for chunk: ${e.message}")
    }

    // 2. Secondary fallback engine: MyMemory API
    try {
      val myMemoryResult = translateWithMyMemory(queryText, targetLang)
      if (!myMemoryResult.isNullOrBlank()) {
        val parsed = parseIndexedTranslations(myMemoryResult, chunk.size, indexMap, chunk)
        if (parsed.any { it.translation.isNotBlank() }) {
          return parsed
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "MyMemory translation failed for chunk: ${e.message}")
    }

    // 3. Tertiary fallback engine: Google single-call translate_a
    try {
      val fallbackResult = translateWithGoogleApiFallback(queryText, targetLang)
      if (!fallbackResult.isNullOrBlank()) {
        val parsed = parseIndexedTranslations(fallbackResult, chunk.size, indexMap, chunk)
        if (parsed.any { it.translation.isNotBlank() }) {
          return parsed
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Google API fallback failed for chunk: ${e.message}")
    }

    // Fallback: return original trimmed lines
    return chunk.map { TranslationResult(translation = it.trim()) }
  }

  private fun translateWithGoogleWeb(
    query: String,
    targetLang: String,
  ): String? {
    val url = HttpUrl.Builder()
      .scheme("https")
      .host("translate.google.com")
      .addPathSegment("m")
      .addQueryParameter("sl", "auto")
      .addQueryParameter("tl", targetLang)
      .addQueryParameter("hl", "en-US")
      .addQueryParameter("q", query)
      .build()

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .get()
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        Log.w(TAG, "Google web translate HTTP ${response.code}")
        return null
      }
      val html = response.body.string()
      val match = RESULT_CONTAINER_REGEX.find(html) ?: return null
      val rawText = match.groupValues[1]
      return unescapeHtml(rawText).trim()
    }
  }

  private fun translateWithMyMemory(
    query: String,
    targetLang: String,
  ): String? {
    val langPair = "autodetect|$targetLang"
    val url = HttpUrl.Builder()
      .scheme("https")
      .host("api.mymemory.translated.net")
      .addPathSegment("get")
      .addQueryParameter("q", query)
      .addQueryParameter("langpair", langPair)
      .build()

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .get()
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        Log.w(TAG, "MyMemory translate HTTP ${response.code}")
        return null
      }
      val bodyStr = response.body.string()
      val jsonElement = json.parseToJsonElement(bodyStr).jsonObject
      val responseData = jsonElement["responseData"]?.jsonObject ?: return null
      val translatedText = responseData["translatedText"]?.jsonPrimitive?.content ?: return null
      return unescapeHtml(translatedText).trim()
    }
  }

  private fun translateWithGoogleApiFallback(
    query: String,
    targetLang: String,
  ): String? {
    val formBody = FormBody.Builder()
      .add("client", "gtx")
      .add("sl", "auto")
      .add("tl", targetLang)
      .add("dt", "t")
      .add("q", query)
      .build()

    val request = Request.Builder()
      .url("https://translate.googleapis.com/translate_a/single")
      .header("User-Agent", USER_AGENT)
      .post(formBody)
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val bodyStr = response.body.string()
      val root = json.parseToJsonElement(bodyStr).jsonArray
      val sentencesArray = root.getOrNull(0)?.jsonArray ?: return null
      val sb = StringBuilder()
      for (element in sentencesArray) {
        val subArray = element.jsonArray
        val trans = subArray.getOrNull(0)?.jsonPrimitive?.content
        if (!trans.isNullOrBlank() && trans != "null") {
          sb.append(trans)
        }
      }
      return sb.toString().trim()
    }
  }

  private fun parseIndexedTranslations(
    translatedText: String,
    chunkSize: Int,
    indexMap: Map<Int, Int>,
    originalChunk: List<String>,
  ): List<TranslationResult> {
    val results = Array(chunkSize) { idx ->
      TranslationResult(translation = "")
    }

    // Normalize Indic/Arabic/Fullwidth digits and brackets so regex matching works across all languages
    val normalizedText = normalizeDigitsAndBrackets(translatedText)

    val parsedByMarker = mutableMapOf<Int, String>()

    for (match in BRACKET_PATTERN.findAll(normalizedText)) {
      val marker = match.groupValues[1].toIntOrNull()
      val content = match.groupValues[2].trim()
      if (marker != null && content.isNotEmpty()) {
        parsedByMarker[marker] = content
      }
    }

    if (parsedByMarker.isNotEmpty()) {
      for ((marker, content) in parsedByMarker) {
        val localIdx = indexMap[marker]
        if (localIdx != null && localIdx in 0 until chunkSize) {
          val orig = originalChunk[localIdx].trim()
          results[localIdx] = TranslationResult(
            translation = content.ifEmpty { orig },
            romanization = content.ifEmpty { orig },
          )
        }
      }
      return results.toList()
    }

    // Fallback: If markers were stripped, try 1-to-1 line matching
    val nonBlankLines = normalizedText.lines().map { cleanLine(it) }.filter { it.isNotEmpty() }
    if (nonBlankLines.size == indexMap.size) {
      val sortedEntries = indexMap.entries.sortedBy { it.key }
      for ((i, entry) in sortedEntries.withIndex()) {
        val localIdx = entry.value
        results[localIdx] = TranslationResult(
          translation = nonBlankLines[i],
          romanization = nonBlankLines[i],
        )
      }
      return results.toList()
    }

    for ((_, localIdx) in indexMap) {
      results[localIdx] = TranslationResult(
        translation = originalChunk[localIdx].trim(),
        romanization = originalChunk[localIdx].trim(),
      )
    }
    return results.toList()
  }

  private fun cleanLine(line: String): String {
    return line.replace(Regex("""^\s*\[?\d+\]?[.:\- ]*"""), "").trim()
  }

  private fun normalizeDigitsAndBrackets(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
      when (c) {
        '［', '【', '(' -> sb.append('[')
        '］', '】', ')' -> sb.append(']')
        // Devanagari / Marathi / Hindi digits (०..९)
        in '\u0966'..'\u096F' -> sb.append((c - '\u0966' + '0'.code).toChar())
        // Bengali digits (০..৯)
        in '\u09E6'..'\u09EF' -> sb.append((c - '\u09E6' + '0'.code).toChar())
        // Arabic-Indic digits (٠..٩)
        in '\u0660'..'\u0669' -> sb.append((c - '\u0660' + '0'.code).toChar())
        // Eastern Arabic-Indic digits (۰..۹)
        in '\u06F0'..'\u06F9' -> sb.append((c - '\u06F0' + '0'.code).toChar())
        // Fullwidth digits (０..９)
        in '\uFF10'..'\uFF19' -> sb.append((c - '\uFF10' + '0'.code).toChar())
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  private fun unescapeHtml(text: String): String {
    var result = text
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&nbsp;", " ")

    // Unescape numeric and hex character entities like &#20320; or &#x4F60;
    if (result.contains("&#")) {
      result = NUMERIC_ENTITY_REGEX.replace(result) { matchResult ->
        val entity = matchResult.groupValues[1]
        try {
          val codePoint = if (entity.startsWith("x", ignoreCase = true)) {
            entity.substring(1).toInt(16)
          } else {
            entity.toInt(10)
          }
          String(Character.toChars(codePoint))
        } catch (_: Exception) {
          matchResult.value
        }
      }
    }

    return result
  }
}
