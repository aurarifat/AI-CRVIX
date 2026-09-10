package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val providerName: String = "OpenRouter"
    override val displayName: String = "OpenRouter AI"

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        val KNOWN_FREE_MODELS = listOf(
            AIModelInfo("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash Exp (Free)", "OpenRouter", true, 1048576, "Ultra Fast"),
            AIModelInfo("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B Instruct (Free)", "OpenRouter", true, 131072, "Fast"),
            AIModelInfo("deepseek/deepseek-r1:free", "DeepSeek R1 Reasoning (Free)", "OpenRouter", true, 65536, "Moderate"),
            AIModelInfo("qwen/qwen-2.5-72b-instruct:free", "Qwen 2.5 72B Instruct (Free)", "OpenRouter", true, 32768, "Fast"),
            AIModelInfo("mistralai/mistral-7b-instruct:free", "Mistral 7B Instruct (Free)", "OpenRouter", true, 32768, "Very Fast"),
            AIModelInfo("microsoft/phi-3-medium-128k-instruct:free", "Phi-3 Medium 128k (Free)", "OpenRouter", true, 128000, "Fast")
        )
    }

    override suspend fun sendMessage(
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("OpenRouter API key is missing. Please set it in Settings."))
            }

            val jsonBody = JSONObject().apply {
                put("model", model)
                val messagesArray = JSONArray()
                messages.forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put("messages", messagesArray)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
            }

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://mayax.ai")
                .header("X-Title", "MayaX AI Assistant")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                        ?: "HTTP ${response.code}: $responseBody"
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(content)
            } else {
                Result.failure(Exception("No choices returned from OpenRouter"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun streamMessage(
        apiKey: String,
        model: String,
        messages: List<ChatMessagePayload>,
        temperature: Float,
        maxTokens: Int,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("OpenRouter API key is missing. Please set it in Settings."))
            }

            val jsonBody = JSONObject().apply {
                put("model", model)
                val messagesArray = JSONArray()
                messages.forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put("messages", messagesArray)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", true)
            }

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://mayax.ai")
                .header("X-Title", "MayaX AI Assistant")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }

            val fullText = StringBuilder()
            val inputStream = response.body?.byteStream() ?: return@withContext Result.failure(Exception("Empty stream"))
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val chunk = delta?.optString("content") ?: ""
                            if (chunk.isNotEmpty()) {
                                fullText.append(chunk)
                                onChunk(chunk)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore parsing errors for partial or ping lines
                    }
                }
            }

            Result.success(fullText.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Directly calls OpenRouter's /api/v1/models endpoint to fetch all models that are currently 100% free.
     * Evaluates :free suffix, zero prompt & completion pricing, and handles JSON parsing robustly.
     */
    suspend fun fetchFreeModelsFromApi(apiKey: String = ""): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$BASE_URL/models")
                .header("HTTP-Referer", "https://mayax.ai")
                .header("X-Title", "MayaX AI Assistant")
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: OpenRouter API returned ${response.message}"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
                ?: return@withContext Result.failure(Exception("Invalid OpenRouter response: missing 'data' array"))

            val freeModels = mutableListOf<AIModelInfo>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.optString("id", "").trim()
                if (id.isBlank()) continue

                val name = obj.optString("name", id).ifBlank { id }
                val description = obj.optString("description", "")
                val context = obj.optInt("context_length", 4096)
                val pricing = obj.optJSONObject("pricing")

                val promptPriceStr = pricing?.optString("prompt", "0") ?: "0"
                val compPriceStr = pricing?.optString("completion", "0") ?: "0"
                val promptNum = promptPriceStr.toDoubleOrNull() ?: pricing?.optDouble("prompt", 0.0) ?: 0.0
                val compNum = compPriceStr.toDoubleOrNull() ?: pricing?.optDouble("completion", 0.0) ?: 0.0

                // OpenRouter explicitly marks free models with ":free" suffix or zero cost pricing
                val isFree = id.endsWith(":free") || id.contains(":free") || (promptNum == 0.0 && compNum == 0.0)

                if (isFree) {
                    freeModels.add(
                        AIModelInfo(
                            id = id,
                            name = name,
                            provider = "OpenRouter",
                            isFree = true,
                            contextLength = context,
                            speed = if (context >= 64000) "High Context" else "Fast",
                            description = description
                        )
                    )
                }
            }

            if (freeModels.isNotEmpty()) {
                val sorted = freeModels.sortedWith(
                    compareBy<AIModelInfo> { model ->
                        when {
                            model.id.contains("gemini", ignoreCase = true) -> 0
                            model.id.contains("llama", ignoreCase = true) -> 1
                            model.id.contains("deepseek", ignoreCase = true) -> 2
                            model.id.contains("qwen", ignoreCase = true) -> 3
                            model.id.contains("mistral", ignoreCase = true) -> 4
                            model.id.contains("phi", ignoreCase = true) -> 5
                            else -> 6
                        }
                    }.thenBy { it.name.lowercase() }
                )
                Result.success(sorted)
            } else {
                Result.success(KNOWN_FREE_MODELS)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listModels(apiKey: String, freeOnly: Boolean): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        if (freeOnly) {
            val freeResult = fetchFreeModelsFromApi(apiKey)
            if (freeResult.isSuccess) {
                return@withContext freeResult
            }
        }

        try {
            val requestBuilder = Request.Builder()
                .url("$BASE_URL/models")
                .header("HTTP-Referer", "https://mayax.ai")
                .header("X-Title", "MayaX AI Assistant")
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                // Return known free models if offline or network failure
                return@withContext Result.success(KNOWN_FREE_MODELS)
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext Result.success(KNOWN_FREE_MODELS)

            val models = mutableListOf<AIModelInfo>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.optString("id", "").trim()
                if (id.isBlank()) continue

                val name = obj.optString("name", id).ifBlank { id }
                val description = obj.optString("description", "")
                val context = obj.optInt("context_length", 4096)
                val pricing = obj.optJSONObject("pricing")
                val promptPrice = pricing?.optString("prompt", "0") ?: "0"
                val compPrice = pricing?.optString("completion", "0") ?: "0"
                val promptNum = promptPrice.toDoubleOrNull() ?: 0.0
                val compNum = compPrice.toDoubleOrNull() ?: 0.0

                val isFree = id.endsWith(":free") || id.contains(":free") || (promptNum == 0.0 && compNum == 0.0)

                if (!freeOnly || isFree) {
                    models.add(
                        AIModelInfo(
                            id = id,
                            name = name,
                            provider = "OpenRouter",
                            isFree = isFree,
                            contextLength = context,
                            speed = if (context > 64000) "High Context" else "Fast",
                            description = description
                        )
                    )
                }
            }

            if (models.isEmpty()) {
                Result.success(KNOWN_FREE_MODELS)
            } else {
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.success(KNOWN_FREE_MODELS)
        }
    }

    override suspend fun healthCheck(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
