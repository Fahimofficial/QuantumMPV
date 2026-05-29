package com.quantummpv.app.repository.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object FreeModelsConfig {
  private val json = Json { ignoreUnknownKeys = true }

  private val defaultPrefixes = mapOf(
    "opencode" to listOf(
      "deepseek-v4-flash-free", "mimo-v2.5-free", "nemotron-3-super-free",
      "big-pickle", "qwen3.6-plus-free", "minimax-m2.5-free",
    ),
    "together" to listOf(
      "meta-llama/", "mistralai/", "google/", "microsoft/",
      "Qwen/", "deepseek-ai/", "allenai/", "upstage/",
    ),
    "openrouter" to listOf(
      "openrouter/auto", "cognitivecomputations/", "google/gemma",
      "microsoft/phi", "mistralai/mistral", "meta-llama/llama",
    ),
    "openai" to listOf(
      "gpt-4o-mini", "gpt-4o-realtime", "o1-mini", "o3-mini",
    ),
    "groq" to listOf(
      "whisper", "distil-whisper", "llama", "gemma", "mixtral", "deepseek",
    ),
    "anthropic" to listOf(
      "claude-3-haiku", "claude-3-5-haiku",
    ),
  )

  private val loadedPrefixes = defaultPrefixes.mapValues { it.value.toMutableList() }.toMutableMap()

  fun getPrefixes(provider: String): List<String> = loadedPrefixes[provider] ?: emptyList()

  fun isFree(provider: String, modelId: String): Boolean {
    return getPrefixes(provider).any { modelId.startsWith(it, ignoreCase = true) }
  }

  @Serializable
  private data class FreeModelsResponse(
    val opencode: List<String>? = null,
    val together: List<String>? = null,
    val openrouter: List<String>? = null,
    val openai: List<String>? = null,
    val groq: List<String>? = null,
    val anthropic: List<String>? = null,
  )

  suspend fun fetch(client: OkHttpClient, url: String = "https://raw.githubusercontent.com/Riteshp2001/mpvRx/master/free-models.json") = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder().url(url).get().build()
      val response = client.newCall(request).execute()
      if (response.isSuccessful) {
        val body = response.body.string()
        val parsed = json.decodeFromString<FreeModelsResponse>(body)
        parsed.opencode?.let { loadedPrefixes["opencode"] = it.toMutableList() }
        parsed.together?.let { loadedPrefixes["together"] = it.toMutableList() }
        parsed.openrouter?.let { loadedPrefixes["openrouter"] = it.toMutableList() }
        parsed.openai?.let { loadedPrefixes["openai"] = it.toMutableList() }
        parsed.groq?.let { loadedPrefixes["groq"] = it.toMutableList() }
        parsed.anthropic?.let { loadedPrefixes["anthropic"] = it.toMutableList() }
      }
    }
  }
}
