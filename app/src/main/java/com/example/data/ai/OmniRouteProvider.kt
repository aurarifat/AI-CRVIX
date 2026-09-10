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

class OmniRouteProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val providerName: String = "OmniRoute"
    override val displayName: String = "OmniRoute AI"

    companion object {
        const val BASE_URL = "https://api.omniroute.ai/v1"
        val KNOWN_FREE_MODELS = listOf(
            AIModelInfo("omni-fast-free", "OmniRoute Fast 3.5 (Free)", "OmniRoute", true, 32768, "Fast"),
            AIModelInfo("omni-balanced-free", "OmniRoute Balanced (Free)", "OmniRoute", true, 65536, "Balanced"),
            AIModelInfo("omni-code-free", "OmniRoute Code Assist (Free)", "OmniRoute", true, 32768, "Fast")
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
                return@withContext Result.failure(IllegalStateException("OmniRoute API key is missing. Please set it in Settings."))
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
            val choices = jsonResponse.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                Result.success(content)
            } else {
                Result.failure(Exception("No choices returned from OmniRoute"))
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
                return@withContext Result.failure(IllegalStateException("OmniRoute API key is missing. Please set it in Settings."))
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
                    } catch (_: Exception) {}
                }
            }

            Result.success(fullText.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listModels(apiKey: String, freeOnly: Boolean): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
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
                return@withContext Result.success(KNOWN_FREE_MODELS)
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext Result.success(KNOWN_FREE_MODELS)

            val models = mutableListOf<AIModelInfo>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", id)
                val isFree = obj.optBoolean("free", false) || id.contains("free")

                if (!freeOnly || isFree) {
                    models.add(
                        AIModelInfo(
                            id = id,
                            name = name,
                            provider = "OmniRoute",
                            isFree = isFree,
                            contextLength = obj.optInt("context_length", 32768),
                            speed = "Fast"
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
