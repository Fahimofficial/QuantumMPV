/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.quantummpv.app.data.lyrics

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

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
  private val okHttpClient: OkHttpClient,
) {
  companion object {
    private const val TAG = "LyricsTranslationService"
    private const val TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
  }

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
        if (text.isNotBlank()) "$original\n$text" else original
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

    val actualTargetLang = when (targetLang) {
      "romaji", "hinglish" -> "en"
      else -> targetLang
    }

    texts.map { text ->
      this@coroutineScope.async(Dispatchers.IO) {
        translateSingleLine(text, actualTargetLang)
      }
    }.awaitAll()
  }

  private fun translateSingleLine(
    text: String,
    targetLang: String,
  ): TranslationResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return TranslationResult("")

    val formBody = FormBody.Builder()
      .add("client", "gtx")
      .add("sl", "auto")
      .add("tl", targetLang)
      .add("dt", "t")
      .add("dt", "rm")
      .add("q", trimmed)
      .build()

    val request = Request.Builder()
      .url(TRANSLATE_URL)
      .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
      .post(formBody)
      .build()

    return try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w(TAG, "Line translation failed HTTP ${response.code}")
          return TranslationResult(translation = trimmed)
        }

        val rawJson = response.body.string()
        parseSingleLineResponse(rawJson, trimmed)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Line translation exception: ${e.message}")
      TranslationResult(translation = trimmed)
    }
  }

  private fun parseSingleLineResponse(
    jsonStr: String,
    originalText: String,
  ): TranslationResult {
    try {
      val root = json.parseToJsonElement(jsonStr).jsonArray
      val sentencesArray = root.getOrNull(0)?.jsonArray ?: return TranslationResult(translation = originalText)
      val detectedLang = root.getOrNull(2)?.jsonPrimitive?.content

      val transBuilder = StringBuilder()
      var romText: String? = null

      for (element in sentencesArray) {
        val subArray = element.jsonArray
        val trans = subArray.getOrNull(0)?.jsonPrimitive?.content
        val rom = subArray.getOrNull(3)?.jsonPrimitive?.content
          ?: subArray.getOrNull(2)?.jsonPrimitive?.content

        if (!trans.isNullOrBlank() && trans != "null") {
          transBuilder.append(trans)
        }
        if (!rom.isNullOrBlank() && rom != "null") {
          romText = rom
        }
      }

      val translated = transBuilder.toString().trim().ifEmpty { originalText }
      val romanized = romText?.trim()?.takeIf { it.isNotBlank() }

      return TranslationResult(
        translation = translated,
        romanization = romanized,
        detectedSourceLang = detectedLang,
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed parsing single line response: ${e.message}")
      return TranslationResult(translation = originalText)
    }
  }
}
