package com.example.devicecontrol

import android.content.Context
import android.content.pm.PackageManager

data class ParsedAction(
    val intent: String,
    val target: String,
    val rawJson: String = ""
)

sealed class ValidationResult {
    data class Valid(val action: ParsedAction, val registeredAction: RegisteredAction) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

class ActionValidator(private val context: Context) {

    fun validate(parsedAction: ParsedAction): ValidationResult {
        val intentUpper = parsedAction.intent.trim().uppercase()
        val registered = ActionRegistry.getAction(intentUpper)
            ?: return ValidationResult.Invalid("Unknown intent '$intentUpper'. Action is not permitted by allowlist.")

        // Target validation
        val target = parsedAction.target.trim()
        if (registered.allowedTargets.isNotEmpty()) {
            val matched = registered.allowedTargets.any { it.equals(target, ignoreCase = true) }
            if (!matched) {
                return ValidationResult.Invalid("Target '$target' is not allowed for intent $intentUpper. Permitted: ${registered.allowedTargets.joinToString()}")
            }
        }

        // For OPEN_APP, verify it's not trying to launch dangerous system internal activities
        if (intentUpper == ActionRegistry.INTENT_OPEN_APP) {
            if (target.isBlank()) {
                return ValidationResult.Invalid("App target cannot be empty.")
            }
            // Check if user specified a package or app name
            val isSafe = isSafeAppTarget(target)
            if (!isSafe) {
                return ValidationResult.Invalid("Target '$target' is restricted or not launchable.")
            }
        }

        return ValidationResult.Valid(parsedAction, registered)
    }

    private fun isSafeAppTarget(target: String): Boolean {
        // Disallow dangerous shell or package injection
        val forbidden = listOf(";", "&", "|", "`", "$", "..", "/", "\\", "rm", "su", "reboot")
        if (forbidden.any { target.contains(it) }) {
            return false
        }
        return true
    }
}
