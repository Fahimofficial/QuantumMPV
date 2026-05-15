package com.quantummpv.app.repository.ai

import android.util.Log
import com.quantummpv.app.preferences.AiPreferences
import com.quantummpv.app.preferences.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AiService(
  private val preferences: AiPreferences,
  private val geminiClient: GeminiClient,
  private val groqClient: GroqClient,
  private val json: Json,
) {
  companion object {
    private const val TAG = "AiService"
  }

  suspend fun fetchModels(): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    when (provider) {
      AiProvider.GEMINI -> geminiClient.fetchModels(apiKey)
      AiProvider.GROQ -> groqClient.fetchModels(apiKey)
    }
  }

  suspend fun verifyKey(): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }

    when (provider) {
      AiProvider.GEMINI -> geminiClient.verifyKey(apiKey)
      AiProvider.GROQ -> groqClient.verifyKey(apiKey)
    }
  }

  suspend fun generateWithAi(
    userInput: String,
    task: AiTask,
  ): Result<String> = withContext(Dispatchers.IO) {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)
    val model = preferences.selectedModel.get()

    if (userInput.isBlank()) {
      return@withContext Result.failure(Exception("Empty input provided to AI"))
    }
    val customPromptEnabled = preferences.customPromptEnabled.get()
    val customPrompt = preferences.customPrompt.get()

    if (apiKey.isBlank()) {
      return@withContext Result.failure(Exception("API key not configured for $provider"))
    }
    if (model.isBlank()) {
      return@withContext Result.failure(Exception("No AI model selected"))
    }

    val instruction = AiPrompts.resolveInstruction(task, customPromptEnabled, customPrompt)

    when (provider) {
      AiProvider.GEMINI -> geminiClient.generateContent(apiKey, model, instruction, userInput)
      AiProvider.GROQ -> groqClient.generateContent(apiKey, model, instruction, userInput)
    }
  }

  suspend fun renameWithAi(
    currentName: String,
    extension: String?,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(currentName, AiTask.RENAME)
    result.map { aiName ->
      val clean = aiName.trim().removeSurrounding("\"").removeSurrounding("'")
      if (extension != null && !clean.endsWith(extension)) {
        "$clean$extension"
      } else clean
    }
  }

  suspend fun formatTitleForSubtitleSearch(
    fileTitle: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    val result = generateWithAi(fileTitle, AiTask.SUBTITLE_FORMAT)
    result.map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
  }

  suspend fun isConfigured(): Boolean {
    val provider = preferences.provider.get()
    val apiKey = getApiKey(provider)
    return preferences.enabled.get() && apiKey.isNotBlank() && preferences.selectedModel.get().isNotBlank()
  }

  private fun getApiKey(provider: AiProvider): String = when (provider) {
    AiProvider.GEMINI -> preferences.geminiApiKey.get()
    AiProvider.GROQ -> preferences.groqApiKey.get()
  }
}
