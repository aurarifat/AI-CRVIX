package com.example.data.ai

import android.content.Context
import android.content.SharedPreferences

enum class ProviderSelection {
    AUTO,
    OMNIROUTE,
    OPENROUTER
}

data class ProviderExecutionResult(
    val content: String,
    val providerUsed: String,
    val modelUsed: String,
    val fallbackOccurred: Boolean = false,
    val fallbackReason: String? = null
)

class ProviderManager(
    private val context: Context,
    val openRouterProvider: OpenRouterProvider = OpenRouterProvider(),
    val omniRouteProvider: OmniRouteProvider = OmniRouteProvider()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mayax_ai_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_PROVIDER_SELECTION = "pref_provider_selection"
        const val PREF_OMNIROUTE_KEY = "pref_omniroute_key"
        const val PREF_OPENROUTER_KEY = "pref_openrouter_key"
        const val PREF_FREE_MODELS_ONLY = "pref_free_models_only"
        const val PREF_SELECTED_MODEL = "pref_selected_model"
        const val PREF_CUSTOM_MODEL = "pref_custom_model"
        const val PREF_TEMPERATURE = "pref_temperature"
        const val PREF_MAX_TOKENS = "pref_max_tokens"
        const val PREF_PERSONALITY = "pref_personality"
        const val PREF_CUSTOM_SYSTEM_PROMPT = "pref_custom_system_prompt"
        const val PREF_VOICE_LANGUAGE = "pref_voice_language"
        const val PREF_SPEECH_RATE = "pref_speech_rate"
        const val PREF_SPEECH_PITCH = "pref_speech_pitch"
        const val PREF_DEVICE_CONTROL_ENABLED = "pref_device_control_enabled"
        const val PREF_ALWAYS_ALLOW_ACTIONS = "pref_always_allow_actions"
        const val PREF_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        const val PREF_DARK_MODE = "pref_dark_mode"
    }

    var providerSelection: ProviderSelection
        get() {
            val name = prefs.getString(PREF_PROVIDER_SELECTION, ProviderSelection.AUTO.name)
            return try { ProviderSelection.valueOf(name ?: ProviderSelection.AUTO.name) } catch (_: Exception) { ProviderSelection.AUTO }
        }
        set(value) = prefs.edit().putString(PREF_PROVIDER_SELECTION, value.name).apply()

    var omniRouteApiKey: String
        get() {
            val saved = prefs.getString(PREF_OMNIROUTE_KEY, "") ?: ""
            if (saved.isNotBlank()) return saved
            return try {
                val key = com.example.BuildConfig.OMNIROUTE_API_KEY.replace("\"", "").trim()
                if (key.equals("placeholder", ignoreCase = true) || key.startsWith("MY_")) "" else key
            } catch (_: Exception) { "" }
        }
        set(value) = prefs.edit().putString(PREF_OMNIROUTE_KEY, value.trim()).apply()

    var openRouterApiKey: String
        get() {
            val saved = prefs.getString(PREF_OPENROUTER_KEY, "") ?: ""
            if (saved.isNotBlank()) return saved
            return try {
                val key = com.example.BuildConfig.OPENROUTER_API_KEY.replace("\"", "").trim()
                if (key.equals("placeholder", ignoreCase = true) || key.startsWith("MY_")) "" else key
            } catch (_: Exception) { "" }
        }
        set(value) = prefs.edit().putString(PREF_OPENROUTER_KEY, value.trim()).apply()

    var freeModelsOnly: Boolean
        get() = prefs.getBoolean(PREF_FREE_MODELS_ONLY, true) // Free models only by default
        set(value) = prefs.edit().putBoolean(PREF_FREE_MODELS_ONLY, value).apply()

    var selectedModel: String
        get() = prefs.getString(PREF_SELECTED_MODEL, "google/gemini-2.0-flash-exp:free") ?: "google/gemini-2.0-flash-exp:free"
        set(value) = prefs.edit().putString(PREF_SELECTED_MODEL, value).apply()

    var customModel: String
        get() = prefs.getString(PREF_CUSTOM_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(PREF_CUSTOM_MODEL, value).apply()

    var temperature: Float
        get() = prefs.getFloat(PREF_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(PREF_TEMPERATURE, value).apply()

    var maxTokens: Int
        get() = prefs.getInt(PREF_MAX_TOKENS, 1024)
        set(value) = prefs.edit().putInt(PREF_MAX_TOKENS, value).apply()

    var personality: String
        get() = prefs.getString(PREF_PERSONALITY, "Friendly") ?: "Friendly"
        set(value) = prefs.edit().putString(PREF_PERSONALITY, value).apply()

    var customSystemPrompt: String
        get() = prefs.getString(PREF_CUSTOM_SYSTEM_PROMPT, "") ?: ""
        set(value) = prefs.edit().putString(PREF_CUSTOM_SYSTEM_PROMPT, value).apply()

    var voiceLanguage: String
        get() = prefs.getString(PREF_VOICE_LANGUAGE, "bn-BD") ?: "bn-BD"
        set(value) = prefs.edit().putString(PREF_VOICE_LANGUAGE, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(PREF_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(PREF_SPEECH_RATE, value).apply()

    var speechPitch: Float
        get() = prefs.getFloat(PREF_SPEECH_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(PREF_SPEECH_PITCH, value).apply()

    var deviceControlEnabled: Boolean
        get() = prefs.getBoolean(PREF_DEVICE_CONTROL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_DEVICE_CONTROL_ENABLED, value).apply()

    var alwaysAllowActions: Set<String>
        get() = prefs.getStringSet(PREF_ALWAYS_ALLOW_ACTIONS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(PREF_ALWAYS_ALLOW_ACTIONS, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(PREF_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(PREF_DARK_MODE, value).apply()

    fun getActiveModel(): String {
        if (customModel.isNotBlank()) {
            if (freeModelsOnly && !isModelPermittedUnderFreePolicy(customModel)) {
                return selectedModel
            }
            return customModel
        }
        return selectedModel
    }

    fun isModelPermittedUnderFreePolicy(modelId: String): Boolean {
        if (!freeModelsOnly) return true
        val lower = modelId.lowercase()
        return lower.endsWith(":free") || lower.contains("free")
    }

    suspend fun fetchOpenRouterFreeModels(apiKey: String = openRouterApiKey): Result<List<AIModelInfo>> {
        return openRouterProvider.fetchFreeModelsFromApi(apiKey)
    }

    suspend fun listAllAvailableModels(refresh: Boolean = false): List<AIModelInfo> {
        val freeOnly = freeModelsOnly
        val openRouterModels = openRouterProvider.listModels(openRouterApiKey, freeOnly).getOrDefault(OpenRouterProvider.KNOWN_FREE_MODELS)
        val omniRouteModels = omniRouteProvider.listModels(omniRouteApiKey, freeOnly).getOrDefault(OmniRouteProvider.KNOWN_FREE_MODELS)

        val combined = mutableListOf<AIModelInfo>()
        combined.addAll(openRouterModels)
        combined.addAll(omniRouteModels)
        return if (freeOnly) {
            combined.filter { it.isFree }
        } else {
            combined
        }
    }

    suspend fun executeChat(
        messages: List<ChatMessagePayload>,
        systemPrompt: String,
        stream: Boolean = false,
        onChunk: ((String) -> Unit)? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<ProviderExecutionResult> {
        val activeModel = getActiveModel()
        val fullMessages = mutableListOf<ChatMessagePayload>()
        if (systemPrompt.isNotBlank()) {
            fullMessages.add(ChatMessagePayload("system", systemPrompt))
        }
        fullMessages.addAll(messages)

        val selection = providerSelection
        val openRouterKey = openRouterApiKey
        val omniRouteKey = omniRouteApiKey

        when (selection) {
            ProviderSelection.OPENROUTER -> {
                if (openRouterKey.isBlank()) {
                    return Result.failure(IllegalStateException("OpenRouter API key is missing. Please set it in Settings."))
                }
                onStatusUpdate?.invoke("Connecting to OpenRouter...")
                val res = if (stream && onChunk != null) {
                    openRouterProvider.streamMessage(openRouterKey, activeModel, fullMessages, temperature, maxTokens, onChunk)
                } else {
                    openRouterProvider.sendMessage(openRouterKey, activeModel, fullMessages, temperature, maxTokens)
                }
                return res.map { ProviderExecutionResult(it, "OpenRouter", activeModel) }
            }

            ProviderSelection.OMNIROUTE -> {
                if (omniRouteKey.isBlank()) {
                    return Result.failure(IllegalStateException("OmniRoute API key is missing. Please set it in Settings."))
                }
                onStatusUpdate?.invoke("Connecting to OmniRoute...")
                val res = if (stream && onChunk != null) {
                    omniRouteProvider.streamMessage(omniRouteKey, activeModel, fullMessages, temperature, maxTokens, onChunk)
                } else {
                    omniRouteProvider.sendMessage(omniRouteKey, activeModel, fullMessages, temperature, maxTokens)
                }
                return res.map { ProviderExecutionResult(it, "OmniRoute", activeModel) }
            }

            ProviderSelection.AUTO -> {
                // Determine primary provider based on configured keys and model preference
                val primaryIsOmni = omniRouteKey.isNotBlank() && activeModel.startsWith("omni")
                val primaryProvider = if (primaryIsOmni) omniRouteProvider else openRouterProvider
                val primaryKey = if (primaryIsOmni) omniRouteKey else openRouterKey
                val primaryName = if (primaryIsOmni) "OmniRoute" else "OpenRouter"
                val primaryDefaultModel = if (primaryIsOmni) "omni-fast-free" else "google/gemini-2.0-flash-exp:free"

                val secondaryProvider = if (primaryIsOmni) openRouterProvider else omniRouteProvider
                val secondaryKey = if (primaryIsOmni) openRouterKey else omniRouteKey
                val secondaryName = if (primaryIsOmni) "OpenRouter" else "OmniRoute"
                val secondaryDefaultModel = if (primaryIsOmni) "google/gemini-2.0-flash-exp:free" else "omni-fast-free"

                // Attempt primary
                if (primaryKey.isNotBlank()) {
                    onStatusUpdate?.invoke("Trying $primaryName...")
                    val modelToUse = if (primaryName == "OmniRoute" && !activeModel.startsWith("omni")) primaryDefaultModel else activeModel
                    val primaryResult = if (stream && onChunk != null) {
                        primaryProvider.streamMessage(primaryKey, modelToUse, fullMessages, temperature, maxTokens, onChunk)
                    } else {
                        primaryProvider.sendMessage(primaryKey, modelToUse, fullMessages, temperature, maxTokens)
                    }

                    if (primaryResult.isSuccess) {
                        return Result.success(
                            ProviderExecutionResult(
                                content = primaryResult.getOrThrow(),
                                providerUsed = primaryName,
                                modelUsed = modelToUse
                            )
                        )
                    }

                    // Primary failed -> fallback
                    val failReason = primaryResult.exceptionOrNull()?.message ?: "Unknown error"
                    onStatusUpdate?.invoke("$primaryName is unavailable. Trying $secondaryName...")

                    if (secondaryKey.isNotBlank()) {
                        val secondaryModel = if (secondaryName == "OmniRoute") secondaryDefaultModel else "google/gemini-2.0-flash-exp:free"
                        val secondaryResult = if (stream && onChunk != null) {
                            secondaryProvider.streamMessage(secondaryKey, secondaryModel, fullMessages, temperature, maxTokens, onChunk)
                        } else {
                            secondaryProvider.sendMessage(secondaryKey, secondaryModel, fullMessages, temperature, maxTokens)
                        }

                        if (secondaryResult.isSuccess) {
                            return Result.success(
                                ProviderExecutionResult(
                                    content = secondaryResult.getOrThrow(),
                                    providerUsed = secondaryName,
                                    modelUsed = secondaryModel,
                                    fallbackOccurred = true,
                                    fallbackReason = "$primaryName failed: $failReason"
                                )
                            )
                        }
                    }
                    return Result.failure(Exception("I couldn't connect to an available AI provider. $primaryName failed: $failReason"))
                } else if (secondaryKey.isNotBlank()) {
                    // Only secondary has key
                    onStatusUpdate?.invoke("Trying $secondaryName...")
                    val secondaryModel = if (secondaryName == "OmniRoute") secondaryDefaultModel else activeModel
                    val secondaryResult = if (stream && onChunk != null) {
                        secondaryProvider.streamMessage(secondaryKey, secondaryModel, fullMessages, temperature, maxTokens, onChunk)
                    } else {
                        secondaryProvider.sendMessage(secondaryKey, secondaryModel, fullMessages, temperature, maxTokens)
                    }
                    return secondaryResult.map { ProviderExecutionResult(it, secondaryName, secondaryModel) }
                } else {
                    return Result.failure(IllegalStateException("No API key configured. Please add an OpenRouter or OmniRoute key in Settings."))
                }
            }
        }
    }
}
