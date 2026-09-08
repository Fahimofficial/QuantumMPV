/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.repository.ai

import java.util.Locale

internal object AiModelCapabilities {
  fun isTextGenerationModel(id: String): Boolean {
    val value = id.lowercase(Locale.ROOT)
    return listOf(
      "whisper",
      "transcribe",
      "tts",
      "speech",
      "embed",
      "rerank",
      "moderation",
      "dall-e",
      "gpt-image",
      "realtime",
      "audio-preview",
      "guard",
      "safety",
    ).none(value::contains)
  }

  fun isSpeechToTextModel(id: String): Boolean {
    val value = id.lowercase(Locale.ROOT)
    if (listOf("tts", "text-to-speech", "speech-generation").any(value::contains)) return false
    return listOf("whisper", "transcribe", "speech-to-text", "speech_to_text").any(value::contains)
  }

  fun normalize(models: List<AiModelInfo>): List<AiModelInfo> =
    models
      .asSequence()
      .filter { it.id.isNotBlank() }
      .distinctBy { it.id.lowercase(Locale.ROOT) }
      .sortedWith(
        compareBy<AiModelInfo> { it.displayName.lowercase(Locale.ROOT) }
          .thenBy { it.id.lowercase(Locale.ROOT) },
      ).toList()
}
