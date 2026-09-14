package com.example.devicecontrol

import org.json.JSONObject

object ActionIntentParser {

    private val ACTION_REGEX = Regex("""ACTION:\s*(\{.+?\})""", RegexOption.DOT_MATCHES_ALL)
    private val ACTION_LIST_REGEX = Regex("""ACTION_LIST:\s*(\[.+?\])""", RegexOption.DOT_MATCHES_ALL)

    fun parse(text: String, userPrompt: String = ""): ParsedAction? {
        // Look for ACTION_LIST:[...] multi-task plan pattern
        val listMatch = ACTION_LIST_REGEX.find(text)
        if (listMatch != null) {
            val jsonArrStr = listMatch.groupValues[1]
            return ParsedAction(
                intent = ActionRegistry.INTENT_TASK_PLAN,
                target = "Task Plan",
                message = jsonArrStr,
                rawJson = jsonArrStr
            )
        }

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

        // Natural fallback from user prompt or assistant confirmation if user asks to open an app or automate a task
        val candidate = userPrompt.trim().ifBlank { text.trim() }
        if (candidate.isNotBlank()) {
            val lower = candidate.lowercase()

            // Direct standalone UI action commands
            if (lower == "close ads" || lower == "close ad" || lower == "dismiss popup" || lower == "skip ad" || lower == "dismiss ads") {
                return ParsedAction(ActionRegistry.INTENT_DISMISS_POPUP, "")
            }
            if (lower == "turn it on" || lower == "turn on" || lower == "enable protection" || lower == "turn on protection") {
                return ParsedAction(ActionRegistry.INTENT_TOGGLE_SWITCH, "turn on")
            }
            if (lower == "read screen" || lower == "read device screen" || lower == "read whole device screen" || lower == "what's on screen" || lower == "what is on my screen" || lower == "inspect screen") {
                return ParsedAction(ActionRegistry.INTENT_READ_SCREEN, "")
            }
            if (Regex("""^tap\s+(\d+)\s+(\d+)""").containsMatchIn(lower)) {
                val coords = Regex("""\d+\s+\d+""").find(lower)?.value ?: ""
                return ParsedAction(ActionRegistry.INTENT_TAP_COORDINATES, coords)
            }

            // Check if user input is an explicit multi-step command (e.g. "open adguard and close ads and turn it on")
            val decomposed = AgentTaskDecomposer.decompose(candidate)
            if (decomposed.tasks.size > 1) {
                return ParsedAction(
                    intent = ActionRegistry.INTENT_TASK_PLAN,
                    target = candidate,
                    message = candidate
                )
            }

            val prefixes = listOf("open app ", "launch app ", "open ", "launch ", "start ")
            for (p in prefixes) {
                if (lower.startsWith(p)) {
                    val raw = candidate.substring(p.length).trim().removeSuffix(".").removeSuffix("!")

                    // Check for multi-step agentic automation: e.g. "open Adguard close ads and turn it on"
                    val agenticRegex = Regex(
                        """^(.*?)(?:\s+(?:and\s+|then\s+|,)\s*|\s+)(close\s+ads?.*|dismiss.*|skip.*|turn\s+(?:it\s+)?on.*|enable.*|start.*|activate.*|connect.*|click\s+.+)$""",
                        RegexOption.IGNORE_CASE
                    )
                    val match = agenticRegex.find(raw)
                    if (match != null) {
                        val extractedApp = match.groupValues[1].trim()
                        val instructions = match.groupValues[2].trim()
                        if (extractedApp.isNotBlank()) {
                            return ParsedAction(
                                intent = ActionRegistry.INTENT_AGENTIC_TASK,
                                target = extractedApp,
                                message = instructions
                            )
                        }
                    }

                    if (raw.isNotBlank() && raw.length < 50 && !raw.contains("question", ignoreCase = true)) {
                        return when (raw.lowercase()) {
                            "youtube" -> ParsedAction(ActionRegistry.INTENT_OPEN_YOUTUBE, "YouTube")
                            "chrome", "google chrome", "browser" -> ParsedAction(ActionRegistry.INTENT_OPEN_CHROME, "Chrome")
                            "calculator" -> ParsedAction(ActionRegistry.INTENT_OPEN_CALCULATOR, "Calculator")
                            "settings" -> ParsedAction(ActionRegistry.INTENT_OPEN_SETTINGS, "Settings")
                            "whatsapp" -> ParsedAction(ActionRegistry.INTENT_OPEN_APP, "WhatsApp")
                            else -> ParsedAction(ActionRegistry.INTENT_OPEN_APP, raw)
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
            var intent = json.optString("intent", "").trim().uppercase()
            var target = json.optString("target", "")
            if (target.isBlank()) {
                target = json.optString("recipient", json.optString("phone", json.optString("query", json.optString("app", ""))))
            }
            val message = json.optString("message", json.optString("text", json.optString("body", json.optString("content", ""))))

            // Detect agentic intent if labeled as OPEN_APP or generic but message contains actions
            val lowerMsg = message.lowercase()
            if (intent == ActionRegistry.INTENT_OPEN_APP || intent == "LAUNCH_APP") {
                if (lowerMsg.contains("close ad") || lowerMsg.contains("close ads") ||
                    lowerMsg.contains("turn on") || lowerMsg.contains("enable") ||
                    lowerMsg.contains("click") || lowerMsg.contains("protection") ||
                    lowerMsg.contains("dismiss")
                ) {
                    intent = ActionRegistry.INTENT_AGENTIC_TASK
                }
            }

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
        return text.replace(ACTION_REGEX, "").replace(ACTION_LIST_REGEX, "").trim()
    }
}
