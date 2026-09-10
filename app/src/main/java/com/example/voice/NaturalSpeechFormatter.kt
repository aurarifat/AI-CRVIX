package com.example.voice

import java.util.regex.Pattern

object NaturalSpeechFormatter {

    private val ACTION_REGEX = Regex("""ACTION:\s*\{.+?\}""", RegexOption.DOT_MATCHES_ALL)
    private val CODE_BLOCK_REGEX = Regex("""```[\s\S]*?```""")
    private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")
    private val URL_REGEX = Regex("""https?://\S+""")
    private val MARKDOWN_HEADER_REGEX = Regex("""^#{1,6}\s+""", RegexOption.MULTILINE)
    private val EMOJI_REGEX = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+""")

    /**
     * Formats AI responses into warm, flowing, natural conversational speech.
     * Removes robotic symbols, raw JSON, markdown formatting, and inserts natural cadence pauses.
     */
    fun format(text: String): String {
        if (text.isBlank()) return ""

        var speech = text

        // 1. Remove Action JSON payloads completely
        speech = speech.replace(ACTION_REGEX, "")

        // 2. Replace code blocks with conversational summaries
        speech = speech.replace(CODE_BLOCK_REGEX, "I have placed the code snippet in our chat.")
        speech = speech.replace(INLINE_CODE_REGEX, "$1")

        // 3. Replace web URLs
        speech = speech.replace(URL_REGEX, "the link")

        // 4. Remove Markdown headers
        speech = speech.replace(MARKDOWN_HEADER_REGEX, "")

        // 5. Replace bullet points (* or - or +) with natural pauses or smooth transitions
        val lines = speech.lines()
        val processedLines = mutableListOf<String>()
        var listIndex = 1

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.substring(2).trim()
                    val prefix = when (listIndex) {
                        1 -> "First, "
                        2 -> "Next, "
                        3 -> "Also, "
                        else -> "And, "
                    }
                    processedLines.add("$prefix$content")
                    listIndex++
                }
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val content = trimmed.replace(Regex("""^\d+\.\s+"""), "").trim()
                    val prefix = when (listIndex) {
                        1 -> "First, "
                        2 -> "Second, "
                        3 -> "Third, "
                        else -> "Next, "
                    }
                    processedLines.add("$prefix$content")
                    listIndex++
                }
                else -> {
                    listIndex = 1
                    if (trimmed.isNotBlank()) {
                        processedLines.add(trimmed)
                    }
                }
            }
        }

        speech = processedLines.joinToString(". ")

        // 6. Remove formatting characters: **, *, _, #, ~, >, |, \, /
        speech = speech.replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("~", "")
            .replace(">", "")
            .replace("|", ", ")
            .replace("\\", "")

        // 7. Strip emojis so the synthesizer doesn't read literal emoji names
        speech = speech.replace(EMOJI_REGEX, "")

        // 8. Add natural cadence pauses after conversational openers
        speech = speech.replace(Regex("""\b(Sure|Certainly|Alright|Okay|Got it|No problem)\s+([A-Z])"""), "$1, $2")

        // 9. Clean up multiple spaces and excessive punctuation
        speech = speech.replace(Regex("""\s+"""), " ")
            .replace(Regex("""\.{2,}"""), ".")
            .replace(Regex(""",{2,}"""), ",")
            .replace(Regex("""\s+,"""), ",")
            .replace(Regex("""\s+\."""), ".")
            .trim()

        return speech
    }
}
