package com.example.devicecontrol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Status of an individual task in a decomposed multi-task plan.
 */
enum class AgentTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Represents a single atomic task in a multi-step sequence (e.g. Task 1, Task 2, Task 3...).
 */
data class AgentTaskItem(
    val taskNumber: Int,
    val title: String,
    val action: ParsedAction,
    var status: AgentTaskStatus = AgentTaskStatus.PENDING,
    var resultMessage: String = ""
)

/**
 * Represents a complete decomposed plan of sequential tasks.
 */
data class AgentTaskPlan(
    val originalPrompt: String,
    val summary: String,
    val tasks: List<AgentTaskItem>
) {
    /**
     * Formats the multi-task plan into human-readable text for chat logs and UI banners.
     */
    fun toFormattedPlanString(): String {
        val sb = StringBuilder()
        sb.append("📋 Decomposed Plan (${tasks.size} Tasks):\n")
        tasks.forEach { task ->
            val icon = when (task.status) {
                AgentTaskStatus.COMPLETED -> "✅"
                AgentTaskStatus.RUNNING -> "⏳"
                AgentTaskStatus.FAILED -> "❌"
                AgentTaskStatus.PENDING -> "•"
            }
            sb.append("$icon Task ${task.taskNumber}: ${task.title}\n")
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Decomposes complex compound user prompts into sequential tasks (Task 1, 2, 3, 4, 5...)
 * and parses multi-action plans.
 */
object AgentTaskDecomposer {

    private val ACTION_LIST_REGEX = Regex("""ACTION_LIST:\s*(\[.+?\])""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Decomposes any input prompt into an ordered list of tasks (Task 1, Task 2, Task 3...).
     */
    fun decompose(prompt: String, parsedAction: ParsedAction? = null): AgentTaskPlan {
        val cleanPrompt = prompt.trim()

        // 1. Check for explicit JSON ACTION_LIST from LLM: ACTION_LIST:[{...}, {...}]
        val actionListMatch = ACTION_LIST_REGEX.find(cleanPrompt)
        if (actionListMatch != null) {
            val jsonArrStr = actionListMatch.groupValues[1]
            val planFromJson = parseActionListJson(jsonArrStr, cleanPrompt)
            if (planFromJson != null && planFromJson.tasks.isNotEmpty()) {
                return planFromJson
            }
        }

        // 2. Check if the parsed action is an AGENTIC_TASK (compound multi-action)
        if (parsedAction != null && parsedAction.intent == ActionRegistry.INTENT_AGENTIC_TASK) {
            val plan = decomposeAgenticCommand(parsedAction.target, parsedAction.message, cleanPrompt)
            if (plan.tasks.isNotEmpty()) {
                return plan
            }
        }

        // Check if prompt matches natural compound agentic pattern e.g. "open adguard close ads and turn it on"
        val compoundAgenticMatch = Regex("""^open\s+([a-zA-Z0-9_\-]+)\s+(close\s+ads?.*|dismiss.*|turn\s+.*|enable.*|click\s+.*)""", RegexOption.IGNORE_CASE).find(cleanPrompt)
        if (compoundAgenticMatch != null) {
            val targetApp = compoundAgenticMatch.groupValues[1]
            val remainder = compoundAgenticMatch.groupValues[2]
            val plan = decomposeAgenticCommand(targetApp, remainder, cleanPrompt)
            if (plan.tasks.isNotEmpty()) {
                return plan
            }
        }

        // 3. Check for explicit numbered task structure: "Task 1: ... Task 2: ... Task 3: ..." or "1. ... 2. ... 3. ..."
        val explicitPlan = parseExplicitNumberedTasks(cleanPrompt)
        if (explicitPlan != null && explicitPlan.tasks.size > 1) {
            return explicitPlan
        }

        // 4. Natural language decomposition using conjunctions ("open X close Y and turn on Z", "open X and then Y then Z")
        val compoundPlan = parseCompoundNaturalPrompt(cleanPrompt)
        if (compoundPlan != null && compoundPlan.tasks.isNotEmpty()) {
            return compoundPlan
        }

        // 5. Fallback: single task
        val fallbackAction = parsedAction ?: ParsedAction(
            intent = ActionRegistry.INTENT_OPEN_APP,
            target = cleanPrompt
        )
        val singleTask = AgentTaskItem(
            taskNumber = 1,
            title = cleanPrompt.ifBlank { "Execute action" },
            action = fallbackAction
        )
        return AgentTaskPlan(
            originalPrompt = cleanPrompt,
            summary = "Task 1: ${singleTask.title}",
            tasks = listOf(singleTask)
        )
    }

    /**
     * Decomposes compound agentic actions such as:
     * target = "Adguard", message = "close ads and turn it on"
     */
    fun decomposeAgenticCommand(targetApp: String, instruction: String, originalPrompt: String): AgentTaskPlan {
        val tasks = mutableListOf<AgentTaskItem>()
        var taskCounter = 1

        val lowerInstruction = instruction.lowercase()

        // Task 1: Open Target App
        if (targetApp.isNotBlank()) {
            val formattedTarget = targetApp.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            tasks.add(
                AgentTaskItem(
                    taskNumber = taskCounter++,
                    title = "Open $formattedTarget",
                    action = ParsedAction(ActionRegistry.INTENT_OPEN_APP, formattedTarget)
                )
            )
        }

        // Task 2: Close Ads / Popups
        val hasCloseAds = lowerInstruction.contains("close ad") || lowerInstruction.contains("close ads") ||
                lowerInstruction.contains("dismiss popup") || lowerInstruction.contains("skip ad") ||
                lowerInstruction.contains("popup") || lowerInstruction.contains("dismiss")
        if (hasCloseAds) {
            tasks.add(
                AgentTaskItem(
                    taskNumber = taskCounter++,
                    title = "Close ads & dismiss popups",
                    action = ParsedAction(ActionRegistry.INTENT_DISMISS_POPUP, "")
                )
            )
        }

        // Task 3: Turn Protection On / Toggle Switch
        val hasTurnOn = lowerInstruction.contains("turn on") || lowerInstruction.contains("turn it on") ||
                lowerInstruction.contains("enable") || lowerInstruction.contains("protection") ||
                lowerInstruction.contains("start") || lowerInstruction.contains("switch") ||
                lowerInstruction.contains("activate") || lowerInstruction.contains("connect")
        if (hasTurnOn) {
            tasks.add(
                AgentTaskItem(
                    taskNumber = taskCounter++,
                    title = "Turn on protection switch",
                    action = ParsedAction(ActionRegistry.INTENT_TOGGLE_SWITCH, "turn on")
                )
            )
        }

        // Task 4: Click specific on-screen text if specified
        if (lowerInstruction.contains("click ")) {
            val clickTarget = lowerInstruction.substringAfter("click ").substringBefore(" and ").trim()
            if (clickTarget.isNotBlank()) {
                tasks.add(
                    AgentTaskItem(
                        taskNumber = taskCounter++,
                        title = "Click '$clickTarget'",
                        action = ParsedAction(ActionRegistry.INTENT_CLICK_TEXT, clickTarget)
                    )
                )
            }
        }

        // Task 5: Go home if requested
        if (lowerInstruction.contains("go home") || lowerInstruction.contains("home screen")) {
            tasks.add(
                AgentTaskItem(
                    taskNumber = taskCounter++,
                    title = "Navigate to Home Screen",
                    action = ParsedAction(ActionRegistry.INTENT_GO_HOME, "Home")
                )
            )
        }

        val summary = "Decomposed into ${tasks.size} sequential tasks for $targetApp"
        return AgentTaskPlan(originalPrompt, summary, tasks)
    }

    /**
     * Parses prompts like:
     * "Task 1: Open Chrome\nTask 2: Search for news\nTask 3: Go home"
     * or "1. Open YouTube 2. Play music 3. Go home"
     */
    private fun parseExplicitNumberedTasks(prompt: String): AgentTaskPlan? {
        val lines = prompt.split(Regex("""(?:\r?\n|(?=\b(?:Task\s+\d+|Step\s+\d+|\d+\.)\b))"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val taskItems = mutableListOf<AgentTaskItem>()
        var counter = 1

        val taskRegex = Regex("""^(?:Task\s*(\d+)[:.]?|Step\s*(\d+)[:.]?|(\d+)[\).:])\s*(.+)$""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = taskRegex.find(line)
            if (match != null) {
                val explicitNum = match.groupValues[1].ifBlank { match.groupValues[2].ifBlank { match.groupValues[3] } }.toIntOrNull() ?: counter
                val taskDesc = match.groupValues[4].trim()
                if (taskDesc.isNotBlank()) {
                    val action = mapDescriptionToAction(taskDesc)
                    taskItems.add(
                        AgentTaskItem(
                            taskNumber = counter++,
                            title = taskDesc,
                            action = action
                        )
                    )
                }
            }
        }

        if (taskItems.size >= 2) {
            return AgentTaskPlan(
                originalPrompt = prompt,
                summary = "Decomposed into ${taskItems.size} tasks",
                tasks = taskItems
            )
        }
        return null
    }

    /**
     * Parses compound natural language prompts separated by connectors like:
     * "and then", "then", "after that", ", and ", "and"
     */
    private fun parseCompoundNaturalPrompt(prompt: String): AgentTaskPlan? {
        val lower = prompt.lowercase().trim()

        // Check if this looks like a compound multi-action prompt
        val hasMultiActionIndicators = lower.contains(" and then ") || lower.contains(" then ") ||
                lower.contains(" after that ") || lower.contains(" and close ") ||
                lower.contains(" and turn ") || lower.contains(" and click ") ||
                lower.contains(" and search ") || lower.contains(" and go ") ||
                lower.contains(" close ads and turn ") || lower.contains(" dismiss popup and ")

        if (!hasMultiActionIndicators) return null

        // Split by logical step connectors
        val splitRegex = Regex("""\s*(?:,\s*and\s+then\s+|\s+and\s+then\s+|,\s*then\s+|\s+then\s+|\s+after\s+that\s+|,\s*and\s+|\s+and\s+(?=close|turn|enable|click|open|launch|search|play|go|send)|(?<=\b(?:ads?|popup)\b)\s+and\s+)\s*""", RegexOption.IGNORE_CASE)
        val rawParts = prompt.split(splitRegex).map { it.trim() }.filter { it.isNotBlank() }

        if (rawParts.size < 2) return null

        val taskItems = mutableListOf<AgentTaskItem>()
        var counter = 1

        for (part in rawParts) {
            val action = mapDescriptionToAction(part)
            val cleanTitle = cleanTaskTitle(part)
            taskItems.add(
                AgentTaskItem(
                    taskNumber = counter++,
                    title = cleanTitle,
                    action = action
                )
            )
        }

        return AgentTaskPlan(
            originalPrompt = prompt,
            summary = "Decomposed into ${taskItems.size} sequential tasks",
            tasks = taskItems
        )
    }

    /**
     * Parses ACTION_LIST JSON array.
     */
    private fun parseActionListJson(jsonStr: String, originalPrompt: String): AgentTaskPlan? {
        return try {
            val jsonArray = JSONArray(jsonStr)
            val tasks = mutableListOf<AgentTaskItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val taskNum = obj.optInt("task", i + 1)
                val title = obj.optString("title", obj.optString("name", "Task $taskNum"))
                val intent = obj.optString("intent", ActionRegistry.INTENT_OPEN_APP).uppercase()
                val target = obj.optString("target", "")
                val message = obj.optString("message", "")

                tasks.add(
                    AgentTaskItem(
                        taskNumber = taskNum,
                        title = title.ifBlank { "Task $taskNum" },
                        action = ParsedAction(intent = intent, target = target, message = message)
                    )
                )
            }

            if (tasks.isNotEmpty()) {
                AgentTaskPlan(
                    originalPrompt = originalPrompt,
                    summary = "Decomposed into ${tasks.size} tasks from AI plan",
                    tasks = tasks
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun mapDescriptionToAction(desc: String): ParsedAction {
        val lower = desc.lowercase().trim()

        if (lower.contains("read screen") || lower.contains("what's on screen") || lower.contains("read device screen") || lower.contains("read whole device screen")) {
            return ParsedAction(ActionRegistry.INTENT_READ_SCREEN, "")
        }

        if (Regex("""^tap\s+(\d+)\s+(\d+)""").containsMatchIn(lower)) {
            val coords = Regex("""\d+\s+\d+""").find(lower)?.value ?: ""
            return ParsedAction(ActionRegistry.INTENT_TAP_COORDINATES, coords)
        }

        if (lower.contains("close ad") || lower.contains("close ads") || lower.contains("dismiss popup") || lower.contains("skip ad")) {
            return ParsedAction(ActionRegistry.INTENT_DISMISS_POPUP, "")
        }

        if (lower.contains("turn on") || lower.contains("turn it on") || lower.contains("enable protection") || lower.contains("turn on switch")) {
            return ParsedAction(ActionRegistry.INTENT_TOGGLE_SWITCH, "turn on")
        }

        if (lower.contains("go home") || lower == "home" || lower.contains("home screen")) {
            return ParsedAction(ActionRegistry.INTENT_GO_HOME, "Home")
        }

        if (lower.startsWith("click ") || lower.startsWith("tap ") || lower.startsWith("press ")) {
            val target = desc.replace(Regex("""^(click|tap|press)\s+""", RegexOption.IGNORE_CASE), "").trim()
            return ParsedAction(ActionRegistry.INTENT_CLICK_TEXT, target)
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val target = desc.replace(Regex("""^(open|launch|start)\s+(app\s+)?""", RegexOption.IGNORE_CASE), "").trim()
            return when (target.lowercase()) {
                "youtube" -> ParsedAction(ActionRegistry.INTENT_OPEN_YOUTUBE, "YouTube")
                "chrome", "browser" -> ParsedAction(ActionRegistry.INTENT_OPEN_CHROME, "Chrome")
                "calculator" -> ParsedAction(ActionRegistry.INTENT_OPEN_CALCULATOR, "Calculator")
                "settings" -> ParsedAction(ActionRegistry.INTENT_OPEN_SETTINGS, "Settings")
                else -> ParsedAction(ActionRegistry.INTENT_OPEN_APP, target)
            }
        }

        if (lower.startsWith("search ")) {
            val query = desc.substringAfter("search ").removePrefix("for ").trim()
            return ParsedAction(ActionRegistry.INTENT_SEARCH_WEB, query)
        }

        if (lower.startsWith("play ")) {
            val song = desc.substringAfter("play ").trim()
            return ParsedAction(ActionRegistry.INTENT_PLAY_YOUTUBE, song)
        }

        return ParsedAction(ActionRegistry.INTENT_AGENTIC_TASK, desc, desc)
    }

    private fun cleanTaskTitle(raw: String): String {
        return raw.trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .removeSuffix(".")
    }
}
