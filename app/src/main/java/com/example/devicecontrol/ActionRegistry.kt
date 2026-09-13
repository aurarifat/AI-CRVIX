package com.example.devicecontrol

data class RegisteredAction(
    val intent: String,
    val description: String,
    val requiresConfirmation: Boolean = false,
    val requiresShizuku: Boolean = false,
    val allowedTargets: List<String> = emptyList()
)

object ActionRegistry {
    const val INTENT_OPEN_APP = "OPEN_APP"
    const val INTENT_OPEN_YOUTUBE = "OPEN_YOUTUBE"
    const val INTENT_OPEN_CHROME = "OPEN_CHROME"
    const val INTENT_OPEN_CALCULATOR = "OPEN_CALCULATOR"
    const val INTENT_OPEN_SETTINGS = "OPEN_SETTINGS"
    const val INTENT_GO_HOME = "GO_HOME"
    const val INTENT_DEVICE_INFO = "DEVICE_INFO"

    // Agentic Messaging & Communications
    const val INTENT_SEND_WHATSAPP = "SEND_WHATSAPP"
    const val INTENT_SEND_SMS = "SEND_SMS"
    const val INTENT_CALL_PHONE = "CALL_PHONE"
    const val INTENT_SEARCH_WEB = "SEARCH_WEB"
    const val INTENT_OPEN_URL = "OPEN_URL"
    const val INTENT_PLAY_YOUTUBE = "PLAY_YOUTUBE"
    const val INTENT_OPEN_ACCESSIBILITY_SETTINGS = "OPEN_ACCESSIBILITY_SETTINGS"

    // Autonomous Agentic UI Automation
    const val INTENT_AGENTIC_TASK = "AGENTIC_TASK"
    const val INTENT_TASK_PLAN = "TASK_PLAN"
    const val INTENT_CLICK_TEXT = "CLICK_TEXT"
    const val INTENT_DISMISS_POPUP = "DISMISS_POPUP"
    const val INTENT_TOGGLE_SWITCH = "TOGGLE_SWITCH"

    // Optional Shizuku/Advanced actions
    const val INTENT_SYSTEM_BACK = "SYSTEM_BACK"
    const val INTENT_SYSTEM_RECENTS = "SYSTEM_RECENTS"

    private val actions = mapOf(
        INTENT_SEND_WHATSAPP to RegisteredAction(
            intent = INTENT_SEND_WHATSAPP,
            description = "Send a WhatsApp message or open chat with pre-filled message",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_SEND_SMS to RegisteredAction(
            intent = INTENT_SEND_SMS,
            description = "Send or compose an SMS message to a contact",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_CALL_PHONE to RegisteredAction(
            intent = INTENT_CALL_PHONE,
            description = "Dial a contact or phone number",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_SEARCH_WEB to RegisteredAction(
            intent = INTENT_SEARCH_WEB,
            description = "Search the web for a query",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_URL to RegisteredAction(
            intent = INTENT_OPEN_URL,
            description = "Open a website URL",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_PLAY_YOUTUBE to RegisteredAction(
            intent = INTENT_PLAY_YOUTUBE,
            description = "Search and play YouTube video",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_ACCESSIBILITY_SETTINGS to RegisteredAction(
            intent = INTENT_OPEN_ACCESSIBILITY_SETTINGS,
            description = "Open Android Accessibility Settings for agentic automation",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_AGENTIC_TASK to RegisteredAction(
            intent = INTENT_AGENTIC_TASK,
            description = "Execute multi-step autonomous app automation (e.g. open app, dismiss ads, click buttons, toggle switches)",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_TASK_PLAN to RegisteredAction(
            intent = INTENT_TASK_PLAN,
            description = "Execute decomposed multi-task plan sequentially (Task 1, 2, 3, 4, 5...)",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_CLICK_TEXT to RegisteredAction(
            intent = INTENT_CLICK_TEXT,
            description = "Click on-screen button or text in the active app",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_DISMISS_POPUP to RegisteredAction(
            intent = INTENT_DISMISS_POPUP,
            description = "Dismiss on-screen ad, popup dialog, or promo banner",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_TOGGLE_SWITCH to RegisteredAction(
            intent = INTENT_TOGGLE_SWITCH,
            description = "Toggle on-screen switch or enable protection",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_APP to RegisteredAction(
            intent = INTENT_OPEN_APP,
            description = "Launch an installed Android application",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_YOUTUBE to RegisteredAction(
            intent = INTENT_OPEN_YOUTUBE,
            description = "Open YouTube video app or website",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_CHROME to RegisteredAction(
            intent = INTENT_OPEN_CHROME,
            description = "Open Chrome web browser",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_CALCULATOR to RegisteredAction(
            intent = INTENT_OPEN_CALCULATOR,
            description = "Open device Calculator",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_OPEN_SETTINGS to RegisteredAction(
            intent = INTENT_OPEN_SETTINGS,
            description = "Open Android Settings page",
            requiresConfirmation = false,
            requiresShizuku = false,
            allowedTargets = listOf("Settings", "WIFI", "BLUETOOTH", "BATTERY", "DISPLAY", "SOUND", "APPS", "DATE")
        ),
        INTENT_GO_HOME to RegisteredAction(
            intent = INTENT_GO_HOME,
            description = "Navigate to Android Home Screen",
            requiresConfirmation = false,
            requiresShizuku = false
        ),
        INTENT_DEVICE_INFO to RegisteredAction(
            intent = INTENT_DEVICE_INFO,
            description = "Read battery, storage, or system info",
            requiresConfirmation = false,
            requiresShizuku = false,
            allowedTargets = listOf("BATTERY", "STORAGE", "DEVICE", "MEMORY")
        ),
        INTENT_SYSTEM_BACK to RegisteredAction(
            intent = INTENT_SYSTEM_BACK,
            description = "Simulate Back button navigation",
            requiresConfirmation = false,
            requiresShizuku = true
        ),
        INTENT_SYSTEM_RECENTS to RegisteredAction(
            intent = INTENT_SYSTEM_RECENTS,
            description = "Show recent applications",
            requiresConfirmation = false,
            requiresShizuku = true
        )
    )

    fun getAction(intent: String): RegisteredAction? = actions[intent.uppercase()]

    fun isActionRegistered(intent: String): Boolean = actions.containsKey(intent.uppercase())

    fun getAllRegisteredActions(): List<RegisteredAction> = actions.values.toList()
}
