package com.quantummpv.app.repository.ai

internal object AiModelCapabilities {
  fun isTextGenerationModel(id: String): Boolean {
    val value = id.lowercase()
    return listOf(
      "whisper",
      "transcribe",
      "tts",
      "speech",
      "embedding",
      "moderation",
      "dall-e",
      "gpt-image",
      "realtime",
      "audio-preview",
      "guard",
      "safety",
    ).none(value::contains)
  }
}
