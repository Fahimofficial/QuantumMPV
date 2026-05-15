package com.quantummpv.app.repository.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiModelInfo(
  val id: String,
  val displayName: String,
)
