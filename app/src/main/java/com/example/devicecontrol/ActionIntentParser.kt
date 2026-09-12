package com.example.devicecontrol

import org.json.JSONObject

object ActionIntentParser {

    private val ACTION_REGEX = Regex("""ACTION:\s*(\{.+?\})""", RegexOption.DOT_MATCHES_ALL)

    fun parse(text: String, userPrompt: String = ""): ParsedAction? {
        // Look for ACTION:{...} pattern
        val match = ACTION_REGEX.find(text)
        if (match != null) {
            val jsonStr = match.groupValues[1]
            return parseJson(jsonStr)
        }

        // Fallback: look for generic json containing "intent"
        val startIdx = text.indexOf("{\"intent\"")
        if (startIdx != -1) {
            val endIdx = text.indexOf("}", startIdx)
            if (endIdx != -1) {
                val jsonStr = text.substring(startIdx, endIdx + 1)
                return parseJson(jsonStr)
            }
        }

        // Natural fallback from user prompt or assistant confirmation if user asks to open an app
        val candidate = userPrompt.trim().ifBlank { text.trim() }
        if (candidate.isNotBlank()) {
            val lower = candidate.lowercase()
            val prefixes = listOf("open app ", "launch app ", "open ", "launch ", "start ")
            for (p in prefixes) {
                if (lower.startsWith(p)) {
                    val appName = candidate.substring(p.length).trim().removeSuffix(".").removeSuffix("!")
                    if (appName.isNotBlank() && appName.length < 50 && !appName.contains("question", ignoreCase = true)) {
                        return when (appName.lowercase()) {
                            "youtube" -> ParsedAction(ActionRegistry.INTENT_OPEN_YOUTUBE, "YouTube")
                            "chrome", "google chrome", "browser" -> ParsedAction(ActionRegistry.INTENT_OPEN_CHROME, "Chrome")
                            "calculator" -> ParsedAction(ActionRegistry.INTENT_OPEN_CALCULATOR, "Calculator")
                            "settings" -> ParsedAction(ActionRegistry.INTENT_OPEN_SETTINGS, "Settings")
                            "whatsapp" -> ParsedAction(ActionRegistry.INTENT_OPEN_APP, "WhatsApp")
                            else -> ParsedAction(ActionRegistry.INTENT_OPEN_APP, appName)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun parseJson(jsonStr: String): ParsedAction? {
        return try {
            val json = JSONObject(jsonStr)
            val intent = json.optString("intent", "")
            var target = json.optString("target", "")
            if (target.isBlank()) {
                target = json.optString("recipient", json.optString("phone", json.optString("query", json.optString("app", ""))))
            }
            val message = json.optString("message", json.optString("text", json.optString("body", json.optString("content", ""))))
            if (intent.isNotBlank()) {
                ParsedAction(intent = intent, target = target, message = message, rawJson = jsonStr)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cleanResponseText(text: String): String {
        return text.replace(ACTION_REGEX, "").trim()
    }
}
