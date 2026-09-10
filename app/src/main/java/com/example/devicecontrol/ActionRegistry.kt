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

    // Optional Shizuku/Advanced actions
    const val INTENT_SYSTEM_BACK = "SYSTEM_BACK"
    const val INTENT_SYSTEM_RECENTS = "SYSTEM_RECENTS"

    private val actions = mapOf(
        INTENT_OPEN_APP to RegisteredAction(
            intent = INTENT_OPEN_APP,
            description = "Launch an installed Android application",
            requiresConfirmation = true,
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
            requiresConfirmation = true,
            requiresShizuku = true
        ),
        INTENT_SYSTEM_RECENTS to RegisteredAction(
            intent = INTENT_SYSTEM_RECENTS,
            description = "Show recent applications",
            requiresConfirmation = true,
            requiresShizuku = true
        )
    )

    fun getAction(intent: String): RegisteredAction? = actions[intent.uppercase()]

    fun isActionRegistered(intent: String): Boolean = actions.containsKey(intent.uppercase())

    fun getAllRegisteredActions(): List<RegisteredAction> = actions.values.toList()
}
