package com.example.devicecontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.data.local.ActionHistoryEntity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ExecutionOutcome(
    val isSuccess: Boolean,
    val userMessage: String,
    val status: String, // "Successful", "Blocked", "Cancelled", "Error"
    val details: String = ""
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

class DeviceActionExecutor(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun getInstalledLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        resolveInfos.mapNotNull { info ->
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            val isSys = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (pkg.isNotBlank() && label.isNotBlank()) {
                InstalledAppItem(label, pkg, isSys)
            } else null
        }.sortedBy { it.appName.lowercase() }
    }

    suspend fun executeAction(action: ParsedAction): ExecutionOutcome = withContext(Dispatchers.Main) {
        val intentUpper = action.intent.trim().uppercase()
        val target = action.target.trim()

        val outcome = try {
            when (intentUpper) {
                ActionRegistry.INTENT_SEND_WHATSAPP -> sendWhatsApp(action)
                ActionRegistry.INTENT_SEND_SMS -> sendSms(action)
                ActionRegistry.INTENT_CALL_PHONE -> callPhone(target)
                ActionRegistry.INTENT_SEARCH_WEB -> searchWeb(target.ifBlank { action.message })
                ActionRegistry.INTENT_OPEN_URL -> openUrl(target)
                ActionRegistry.INTENT_PLAY_YOUTUBE -> playYouTube(target)
                ActionRegistry.INTENT_OPEN_ACCESSIBILITY_SETTINGS -> openAccessibilitySettings()
                ActionRegistry.INTENT_AGENTIC_TASK -> executeAgenticTask(action)
                ActionRegistry.INTENT_CLICK_TEXT -> executeClickText(target)
                ActionRegistry.INTENT_DISMISS_POPUP -> executeDismissPopup()
                ActionRegistry.INTENT_TOGGLE_SWITCH -> executeToggleSwitch(target)
                ActionRegistry.INTENT_OPEN_APP -> launchApp(target)
                ActionRegistry.INTENT_OPEN_YOUTUBE -> launchYouTube()
                ActionRegistry.INTENT_OPEN_CHROME -> launchChrome()
                ActionRegistry.INTENT_OPEN_CALCULATOR -> launchCalculator()
                ActionRegistry.INTENT_OPEN_SETTINGS -> launchSettings(target)
                ActionRegistry.INTENT_GO_HOME -> goHome()
                ActionRegistry.INTENT_DEVICE_INFO -> readDeviceInfo(target)
                ActionRegistry.INTENT_SYSTEM_BACK -> simulateBack()
                ActionRegistry.INTENT_SYSTEM_RECENTS -> simulateRecents()
                else -> ExecutionOutcome(false, "Unsupported action: $intentUpper", "Blocked", "Action not in registry")
            }
        } catch (e: Exception) {
            ExecutionOutcome(false, "Failed to execute: ${e.message}", "Error", e.localizedMessage ?: "")
        }

        // Record in Action History
        withContext(Dispatchers.IO) {
            database.actionHistoryDao().insertAction(
                ActionHistoryEntity(
                    actionType = intentUpper,
                    target = target,
                    status = outcome.status,
                    details = outcome.userMessage
                )
            )
        }

        outcome
    }

    private fun launchApp(target: String): ExecutionOutcome {
        val pm = context.packageManager
        // 1. Direct package check
        var launchIntent = pm.getLaunchIntentForPackage(target)

        // 2. Search installed apps by name match
        if (launchIntent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val matched = resolveInfos.find {
                val label = it.loadLabel(pm).toString()
                label.equals(target, ignoreCase = true) || label.contains(target, ignoreCase = true)
            }
            if (matched != null) {
                launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            }
        }

        // 3. Fallbacks for well-known app names
        if (launchIntent == null) {
            val lower = target.lowercase()
            when {
                lower.contains("youtube") -> return launchYouTube()
                lower.contains("chrome") || lower.contains("browser") -> return launchChrome()
                lower.contains("calc") -> return launchCalculator()
                lower.contains("setting") -> return launchSettings("Settings")
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ExecutionOutcome(true, "Opened $target successfully", "Successful", "App launched")
        } else {
            ExecutionOutcome(false, "Could not find installed app '$target'", "Blocked", "Package not found")
        }
    }

    private fun launchYouTube(): ExecutionOutcome {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionOutcome(true, "Opened YouTube", "Successful")
        } catch (_: Exception) {
            // Browser fallback
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ExecutionOutcome(true, "Opened YouTube in browser", "Successful")
        }
    }

    private fun launchChrome(): ExecutionOutcome {
        val pm = context.packageManager
        val chromeIntent = pm.getLaunchIntentForPackage("com.android.chrome")
        return if (chromeIntent != null) {
            chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chromeIntent)
            ExecutionOutcome(true, "Opened Chrome", "Successful")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ExecutionOutcome(true, "Opened default browser", "Successful")
        }
    }

    private fun launchCalculator(): ExecutionOutcome {
        val calcIntent = Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_APP_CALCULATOR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(calcIntent)
            ExecutionOutcome(true, "Opened Calculator", "Successful")
        } catch (_: Exception) {
            // Try common packages
            val pkgs = listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator"
            )
            for (pkg in pkgs) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ExecutionOutcome(true, "Opened Calculator", "Successful")
                }
            }
            ExecutionOutcome(false, "No calculator app found on device", "Blocked")
        }
    }

    private fun launchSettings(target: String): ExecutionOutcome {
        val action = when (target.uppercase()) {
            "WIFI" -> Settings.ACTION_WIFI_SETTINGS
            "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "BATTERY" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "DISPLAY" -> Settings.ACTION_DISPLAY_SETTINGS
            "SOUND" -> Settings.ACTION_SOUND_SETTINGS
            "APPS" -> Settings.ACTION_APPLICATION_SETTINGS
            "DATE" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Opened $target settings", "Successful")
    }

    private fun goHome(): ExecutionOutcome {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Returned to Home screen", "Successful")
    }

    private fun readDeviceInfo(target: String): ExecutionOutcome {
        return when (target.uppercase()) {
            "BATTERY" -> {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, filter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val statusText = if (isCharging) "Charging" else "Discharging"
                ExecutionOutcome(
                    true,
                    "Battery: $batteryPct% ($statusText)",
                    "Successful",
                    "Level: $batteryPct%, Status: $statusText"
                )
            }

            "STORAGE" -> {
                val stat = StatFs(Environment.getDataDirectory().path)
                val bytesAvailable = stat.availableBytes
                val bytesTotal = stat.totalBytes
                val freeGb = String.format("%.1f", bytesAvailable.toDouble() / (1024 * 1024 * 1024))
                val totalGb = String.format("%.1f", bytesTotal.toDouble() / (1024 * 1024 * 1024))
                ExecutionOutcome(
                    true,
                    "Storage: $freeGb GB free of $totalGb GB total",
                    "Successful",
                    "Free: $freeGb GB, Total: $totalGb GB"
                )
            }

            "DEVICE" -> {
                val model = Build.MODEL
                val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val version = Build.VERSION.RELEASE
                val sdk = Build.VERSION.SDK_INT
                ExecutionOutcome(
                    true,
                    "Device: $brand $model (Android $version, API $sdk)",
                    "Successful",
                    "Device details retrieved"
                )
            }

            "MEMORY" -> {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                val availMb = memInfo.availMem / (1024 * 1024)
                val totalMb = memInfo.totalMem / (1024 * 1024)
                ExecutionOutcome(
                    true,
                    "RAM: $availMb MB available / $totalMb MB total",
                    "Successful"
                )
            }

            else -> ExecutionOutcome(false, "Unknown device info query '$target'", "Blocked")
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendWhatsApp(action: ParsedAction): ExecutionOutcome {
        val rawTarget = action.target.trim()
        val message = action.message.trim().ifBlank {
            if (rawTarget.contains(":")) rawTarget.substringAfter(":").trim() else ""
        }
        val recipient = if (rawTarget.contains(":")) rawTarget.substringBefore(":").trim() else rawTarget

        val resolvedPhone = ContactHelper.resolvePhoneNumber(context, recipient)

        val pm = context.packageManager
        val hasWhatsApp = isPackageInstalled(pm, "com.whatsapp")
        val hasWhatsAppBusiness = isPackageInstalled(pm, "com.whatsapp.w4b")
        val waPackage = if (hasWhatsApp) "com.whatsapp" else if (hasWhatsAppBusiness) "com.whatsapp.w4b" else null

        if (MayaAccessibilityService.isRunning()) {
            MayaAccessibilityService.queueAutoSend(waPackage ?: "com.whatsapp")
        }

        val encodedMsg = Uri.encode(message)

        return try {
            if (!resolvedPhone.isNullOrBlank()) {
                val cleanPhone = resolvedPhone.filter { it.isDigit() }
                val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    if (waPackage != null) setPackage(waPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val statusText = if (MayaAccessibilityService.isRunning()) "Auto-sending message..." else "Opened WhatsApp chat with pre-filled message."
                ExecutionOutcome(
                    true,
                    "Messaging $recipient on WhatsApp: \"$message\". $statusText",
                    "Successful",
                    "Target phone: $cleanPhone"
                )
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    if (waPackage != null) setPackage(waPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (sendIntent.resolveActivity(pm) != null) {
                    context.startActivity(sendIntent)
                    ExecutionOutcome(
                        true,
                        "Opened WhatsApp for $recipient with message: \"$message\"",
                        "Successful",
                        "Pre-filled message in WhatsApp"
                    )
                } else {
                    val webUri = Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    ExecutionOutcome(
                        true,
                        "Opened WhatsApp with message: \"$message\"",
                        "Successful"
                    )
                }
            }
        } catch (e: Exception) {
            if (waPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(waPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionOutcome(true, "Opened WhatsApp. Please send message to $recipient: \"$message\"", "Successful")
                }
            }
            ExecutionOutcome(false, "Could not open WhatsApp: ${e.message}", "Error", e.localizedMessage ?: "")
        }
    }

    private fun sendSms(action: ParsedAction): ExecutionOutcome {
        val recipient = action.target.trim()
        val message = action.message.trim()
        val resolvedPhone = ContactHelper.resolvePhoneNumber(context, recipient) ?: recipient

        val uri = if (resolvedPhone.isNotBlank()) {
            Uri.parse("smsto:${Uri.encode(resolvedPhone)}")
        } else {
            Uri.parse("smsto:")
        }
        val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(smsIntent)
            ExecutionOutcome(true, "Opened SMS for $recipient with message: \"$message\"", "Successful")
        } catch (e: Exception) {
            ExecutionOutcome(false, "Could not send SMS: ${e.message}", "Error")
        }
    }

    private fun callPhone(target: String): ExecutionOutcome {
        val resolvedPhone = ContactHelper.resolvePhoneNumber(context, target) ?: target
        val cleanPhone = resolvedPhone.filter { it.isDigit() || it == '+' }
        if (cleanPhone.isBlank()) {
            return ExecutionOutcome(false, "Could not find a phone number for '$target'", "Blocked")
        }
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(dialIntent)
            ExecutionOutcome(true, "Dialing $target ($cleanPhone)", "Successful")
        } catch (e: Exception) {
            ExecutionOutcome(false, "Could not open dialer: ${e.message}", "Error")
        }
    }

    private fun searchWeb(query: String): ExecutionOutcome {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionOutcome(true, "Searching web for: $query", "Successful")
        } catch (e: Exception) {
            ExecutionOutcome(false, "Could not perform web search: ${e.message}", "Error")
        }
    }

    private fun openUrl(url: String): ExecutionOutcome {
        val fixedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionOutcome(true, "Opened $fixedUrl", "Successful")
        } catch (e: Exception) {
            ExecutionOutcome(false, "Could not open link: ${e.message}", "Error")
        }
    }

    private fun playYouTube(query: String): ExecutionOutcome {
        val encoded = Uri.encode(query)
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:results?search_query=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(appIntent)
            ExecutionOutcome(true, "Searching YouTube for '$query'", "Successful")
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ExecutionOutcome(true, "Searching YouTube for '$query'", "Successful")
        }
    }

    private fun openAccessibilitySettings(): ExecutionOutcome {
        MayaAccessibilityService.openAccessibilitySettings(context)
        return ExecutionOutcome(true, "Opened Accessibility Settings. Enable 'MayaX Agentic Assistant Service' for full automation.", "Successful")
    }

    private suspend fun simulateBack(): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val success = accessibility.performBack()
            return ExecutionOutcome(success, if (success) "Navigated Back" else "Could not perform Back", if (success) "Successful" else "Error")
        }
        val bridge = ShizukuBridge.getInstance(context)
        val status = bridge.status.value
        return if (status.isRunning && status.isPermissionGranted) {
            val result = bridge.inputBack()
            ExecutionOutcome(result.isSuccess, if (result.isSuccess) "Navigated Back via Shizuku" else "Shizuku Back failed: ${result.stderr}", if (result.isSuccess) "Successful" else "Error")
        } else {
            ExecutionOutcome(false, "Enable MayaX Accessibility Service or connect Shizuku for automated navigation", "Blocked", "Service not connected")
        }
    }

    private suspend fun simulateRecents(): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val success = accessibility.performRecents()
            return ExecutionOutcome(success, if (success) "Opened Recent Apps" else "Could not open Recents", if (success) "Successful" else "Error")
        }
        val bridge = ShizukuBridge.getInstance(context)
        val status = bridge.status.value
        return if (status.isRunning && status.isPermissionGranted) {
            val result = bridge.inputRecents()
            ExecutionOutcome(result.isSuccess, if (result.isSuccess) "Opened Recent Apps via Shizuku" else "Shizuku Recents failed: ${result.stderr}", if (result.isSuccess) "Successful" else "Error")
        } else {
            ExecutionOutcome(false, "Enable MayaX Accessibility Service or connect Shizuku for recent apps", "Blocked", "Service not connected")
        }
    }

    // =========================================================================
    // Autonomous Multi-Step Agentic UI Automation
    // =========================================================================

    private suspend fun executeAgenticTask(action: ParsedAction): ExecutionOutcome {
        val targetApp = action.target.trim()
        val instruction = action.message.trim().ifBlank { targetApp }.lowercase()

        // 0. Ensure display overlay permission and Shizuku privileges
        AgentOverlayManager.ensureOverlayPermission(context)

        // Show floating HUD banner at the top of the screen
        AgentOverlayManager.show(
            context = context,
            appName = targetApp,
            initialStatus = "Opening $targetApp...",
            initialCountdown = 3
        )

        // 1. Launch the target app first
        val launchOutcome = launchApp(targetApp)
        if (!launchOutcome.isSuccess) {
            AgentOverlayManager.update(
                title = targetApp,
                status = "Failed to launch $targetApp: ${launchOutcome.userMessage}",
                icon = "❌"
            )
            delay(2000L)
            AgentOverlayManager.dismiss()
            return launchOutcome
        }

        // 2. Ensure accessibility service is active; if not, attempt auto-enable via Shizuku
        if (!MayaAccessibilityService.isRunning()) {
            val bridge = ShizukuBridge.getInstance(context)
            val status = bridge.status.value
            if (status.isRunning && status.isPermissionGranted) {
                bridge.tryEnableAccessibilityService()
                delay(1000L)
            }
        }

        val accessibility = MayaAccessibilityService.instance
        val bridge = ShizukuBridge.getInstance(context)
        val shizukuReady = bridge.status.value.isRunning && bridge.status.value.isPermissionGranted

        val wantsCloseAds = instruction.contains("close ad") || instruction.contains("close ads") ||
                instruction.contains("ad") || instruction.contains("ads") ||
                instruction.contains("popup") || instruction.contains("dismiss") ||
                instruction.contains("skip")

        val wantsTurnOn = instruction.contains("turn on") || instruction.contains("turn it on") ||
                instruction.contains("enable") || instruction.contains("start") ||
                instruction.contains("protection") || instruction.contains("connect") ||
                instruction.contains("switch") || instruction.contains("activate")

        val logs = mutableListOf<String>()
        logs.add("Launched $targetApp")

        // 3. Smooth countdown while waiting for the target app UI to settle (3s left, 2s left, 1s left)
        for (sec in 3 downTo 1) {
            AgentOverlayManager.update(
                title = targetApp,
                status = "Opened $targetApp • Loading UI...",
                countdownSeconds = sec,
                progressPercent = ((4 - sec) * 25),
                icon = "⏳"
            )
            delay(1000L)
        }

        // 4. Handle "close ads" or popups
        if (wantsCloseAds) {
            AgentOverlayManager.update(
                title = targetApp,
                status = "Closing promo ads & popups...",
                countdownSeconds = null,
                progressPercent = 60,
                icon = "🛡️"
            )

            var adDismissed = false
            if (accessibility != null) {
                for (attempt in 1..3) {
                    if (accessibility.dismissPopupsOrAds()) {
                        adDismissed = true
                        logs.add("Dismissed promo/ad popup via Accessibility")
                        break
                    }
                    delay(500L)
                }
            }
            if (!adDismissed && shizukuReady) {
                bridge.inputBack()
                logs.add("Sent Back event via Shizuku to dismiss overlay")
                adDismissed = true
            }

            if (adDismissed) {
                AgentOverlayManager.update(
                    title = targetApp,
                    status = "Closed ad popup ✓",
                    countdownSeconds = null,
                    progressPercent = 75,
                    icon = "✅"
                )
                delay(600L)
            }
        }

        // 5. Handle "turn it on" / "enable protection"
        if (wantsTurnOn) {
            AgentOverlayManager.update(
                title = targetApp,
                status = "Turning protection ON...",
                countdownSeconds = null,
                progressPercent = 85,
                icon = "⚡"
            )

            var turnedOn = false
            if (accessibility != null) {
                for (attempt in 1..3) {
                    if (accessibility.toggleSwitchOrProtection()) {
                        turnedOn = true
                        logs.add("Activated protection/toggle via Accessibility")
                        break
                    }
                    delay(600L)
                }
            }
            if (!turnedOn && shizukuReady) {
                val dm = context.resources.displayMetrics
                val midX = dm.widthPixels / 2
                val midY = (dm.heightPixels * 0.48).toInt()
                bridge.inputTap(midX, midY)
                logs.add("Tapped center action toggle via Shizuku ($midX, $midY)")
                turnedOn = true
            }

            if (turnedOn) {
                AgentOverlayManager.update(
                    title = targetApp,
                    status = "Protection turned ON ✓",
                    countdownSeconds = null,
                    progressPercent = 95,
                    icon = "🛡️"
                )
                delay(600L)
            } else if (accessibility == null && !shizukuReady) {
                logs.add("App opened. Please enable MayaX Accessibility Service in Settings to click on-screen buttons automatically.")
                AgentOverlayManager.complete(
                    successMessage = "Opened $targetApp (Enable MayaX Accessibility for full auto-clicks)",
                    autoDismissDelayMs = 3000L
                )
                return ExecutionOutcome(
                    isSuccess = true,
                    userMessage = "Opened $targetApp. Tip: Enable MayaX Accessibility Service for full autonomous on-screen clicks.",
                    status = "Successful",
                    details = logs.joinToString("; ")
                )
            }
        }

        // 6. Handle arbitrary click requests e.g. "click [target]"
        if (instruction.contains("click ")) {
            val clickTarget = instruction.substringAfter("click ").trim()
            if (clickTarget.isNotBlank()) {
                AgentOverlayManager.update(
                    title = targetApp,
                    status = "Clicking '$clickTarget'...",
                    countdownSeconds = null,
                    progressPercent = 90,
                    icon = "👆"
                )
                if (accessibility != null && accessibility.clickByText(clickTarget)) {
                    logs.add("Clicked '$clickTarget' on screen")
                    delay(400L)
                }
            }
        }

        val summaryMsg = if (wantsTurnOn && wantsCloseAds) {
            "Opened $targetApp, dismissed ads, and turned protection ON!"
        } else if (wantsTurnOn) {
            "Opened $targetApp and enabled protection!"
        } else if (wantsCloseAds) {
            "Opened $targetApp and closed ads."
        } else {
            "Completed automation for $targetApp."
        }

        // Complete the HUD overlay and auto-dismiss after user sees success
        AgentOverlayManager.complete(
            successMessage = summaryMsg,
            autoDismissDelayMs = 3200L
        )

        return ExecutionOutcome(
            isSuccess = true,
            userMessage = summaryMsg,
            status = "Successful",
            details = logs.joinToString("; ")
        )
    }

    private suspend fun executeDismissPopup(): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Closing ad/popup...", null)
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val dismissed = accessibility.dismissPopupsOrAds()
            val result = if (dismissed) {
                ExecutionOutcome(true, "Closed ad/popup on screen", "Successful")
            } else {
                val backed = accessibility.performBack()
                ExecutionOutcome(backed, if (backed) "Dismissed dialog" else "No popup or close button found", if (backed) "Successful" else "Blocked")
            }
            AgentOverlayManager.complete(result.userMessage)
            return result
        }
        val bridge = ShizukuBridge.getInstance(context)
        if (bridge.status.value.isRunning && bridge.status.value.isPermissionGranted) {
            val res = bridge.inputBack()
            val result = ExecutionOutcome(res.isSuccess, "Sent Back key to dismiss popup", if (res.isSuccess) "Successful" else "Error")
            AgentOverlayManager.complete(result.userMessage)
            return result
        }
        AgentOverlayManager.dismiss()
        return ExecutionOutcome(false, "Enable MayaX Accessibility Service to dismiss popups automatically", "Blocked")
    }

    private suspend fun executeToggleSwitch(target: String): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Toggling protection switch...", null)
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val keywords = if (target.isNotBlank()) listOf(target) else emptyList()
            val toggled = accessibility.toggleSwitchOrProtection(keywords)
            val outcome = if (toggled) {
                ExecutionOutcome(true, "Turned on switch / protection", "Successful")
            } else {
                ExecutionOutcome(false, "Could not find a toggle switch on screen", "Blocked")
            }
            AgentOverlayManager.complete(outcome.userMessage)
            return outcome
        }
        val bridge = ShizukuBridge.getInstance(context)
        if (bridge.status.value.isRunning && bridge.status.value.isPermissionGranted) {
            val dm = context.resources.displayMetrics
            val midX = dm.widthPixels / 2
            val midY = (dm.heightPixels * 0.48).toInt()
            bridge.inputTap(midX, midY)
            val outcome = ExecutionOutcome(true, "Tapped center action toggle via Shizuku", "Successful")
            AgentOverlayManager.complete(outcome.userMessage)
            return outcome
        }
        AgentOverlayManager.dismiss()
        return ExecutionOutcome(false, "Enable MayaX Accessibility Service to toggle switches automatically", "Blocked")
    }

    private suspend fun executeClickText(target: String): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Clicking '$target'...", null)
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val clicked = accessibility.clickByText(target)
            val outcome = if (clicked) {
                ExecutionOutcome(true, "Clicked '$target' on screen", "Successful")
            } else {
                ExecutionOutcome(false, "Could not find '$target' on screen", "Blocked")
            }
            AgentOverlayManager.complete(outcome.userMessage)
            return outcome
        }
        AgentOverlayManager.dismiss()
        return ExecutionOutcome(false, "Enable MayaX Accessibility Service to click on-screen text", "Blocked")
    }
}
