package com.example.data.ai

data class AIModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val isFree: Boolean,
    val contextLength: Int = 4096,
    val speed: String = "Fast",
    val description: String = ""
)

data class ChatMessagePayload(
    val role: String,
    val content: String
)

interface AIProvider {
    val providerName: String
    val displayName: String

    suspend fun sendMessage(
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): Result<String>

    suspend fun streamMessage(
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        onChunk: (String) -> Unit
    ): Result<String>

    suspend fun listModels(apiKey: String, freeOnly: Boolean): Result<List<AIModelInfo>>

    suspend fun healthCheck(apiKey: String): Result<Boolean>
}
