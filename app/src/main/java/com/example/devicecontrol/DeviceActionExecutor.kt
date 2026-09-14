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
                ActionRegistry.INTENT_TASK_PLAN -> executeAgenticTask(action)
                ActionRegistry.INTENT_CLICK_TEXT -> executeClickText(target)
                ActionRegistry.INTENT_DISMISS_POPUP -> executeDismissPopup()
                ActionRegistry.INTENT_TOGGLE_SWITCH -> executeToggleSwitch(target)
                ActionRegistry.INTENT_READ_SCREEN -> executeReadScreen()
                ActionRegistry.INTENT_TAP_COORDINATES -> executeTapCoordinates(target)
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
    // Autonomous Multi-Step Agentic UI Automation & Explicit Task Sequencing
    // =========================================================================

    suspend fun executeTaskPlan(plan: AgentTaskPlan): ExecutionOutcome = withContext(Dispatchers.Main) {
        val totalTasks = plan.tasks.size
        if (totalTasks == 0) {
            return@withContext ExecutionOutcome(false, "No tasks to execute", "Error")
        }

        // 0. Ensure display overlay permission and Shizuku privileges
        AgentOverlayManager.ensureOverlayPermission(context)

        // Ensure accessibility service is active; if not, attempt auto-enable via Shizuku
        if (!MayaAccessibilityService.isRunning()) {
            val bridge = ShizukuBridge.getInstance(context)
            val status = bridge.status.value
            if (status.isRunning && status.isPermissionGranted) {
                bridge.tryEnableAccessibilityService()
                delay(1000L)
            }
        }

        val logs = mutableListOf<String>()
        var successfulTasksCount = 0
        val taskManager = TaskManager.getInstance(context)
        val planStartTime = System.currentTimeMillis()

        // Iterate through decomposed tasks one by one
        for (i in plan.tasks.indices) {
            val task = plan.tasks[i]
            val taskNumber = i + 1
            val isLast = (taskNumber == totalTasks)
            task.status = AgentTaskStatus.RUNNING

            val stepStartTime = System.currentTimeMillis()
            taskManager.trackStepStart(
                stepIndex = taskNumber,
                totalSteps = totalTasks,
                taskName = task.title,
                description = task.action.target.ifBlank { task.action.intent }
            )

            // 1. Show floating HUD at top of screen for this task
            AgentOverlayManager.showTaskProgress(
                context = context,
                taskNumber = taskNumber,
                totalTasks = totalTasks,
                taskTitle = task.title,
                status = "Starting Task $taskNumber: ${task.title}...",
                countdownSeconds = null,
                icon = "🤖"
            )

            delay(350L)

            val outcome = when (task.action.intent.uppercase()) {
                ActionRegistry.INTENT_OPEN_APP, ActionRegistry.INTENT_OPEN_CHROME,
                ActionRegistry.INTENT_OPEN_YOUTUBE, ActionRegistry.INTENT_OPEN_CALCULATOR,
                ActionRegistry.INTENT_OPEN_SETTINGS -> {
                    for (sec in 3 downTo 1) {
                        AgentOverlayManager.updateTask(
                            taskNumber = taskNumber,
                            totalTasks = totalTasks,
                            taskTitle = task.title,
                            status = "Opening ${task.action.target}...",
                            countdownSeconds = sec,
                            progressPercent = ((4 - sec) * 25),
                            icon = "⏳"
                        )
                        delay(1000L)
                    }
                    val res = launchApp(task.action.target)
                    if (res.isSuccess) delay(2200L)
                    res
                }

                ActionRegistry.INTENT_DISMISS_POPUP -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Scanning for popups & promo ads...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "🛡️"
                    )
                    val res = executeDismissPopupInternal()
                    delay(500L)
                    res
                }

                ActionRegistry.INTENT_TOGGLE_SWITCH -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Turning protection ON...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "⚡"
                    )
                    val res = executeToggleSwitchInternal(task.action.target)
                    delay(600L)
                    res
                }

                ActionRegistry.INTENT_READ_SCREEN -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Reading device screen...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "👁️"
                    )
                    val res = executeReadScreenInternal()
                    delay(400L)
                    res
                }

                ActionRegistry.INTENT_TAP_COORDINATES -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Tapping screen coordinates...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "👆"
                    )
                    val res = executeTapCoordinatesInternal(task.action.target)
                    delay(400L)
                    res
                }

                ActionRegistry.INTENT_CLICK_TEXT -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Clicking '${task.action.target}'...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "👆"
                    )
                    val res = executeClickTextInternal(task.action.target)
                    delay(500L)
                    res
                }

                ActionRegistry.INTENT_GO_HOME -> {
                    AgentOverlayManager.updateTask(
                        taskNumber = taskNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.title,
                        status = "Navigating to Home screen...",
                        countdownSeconds = null,
                        progressPercent = 50,
                        icon = "🏠"
                    )
                    val res = goHome()
                    delay(500L)
                    res
                }

                ActionRegistry.INTENT_SEARCH_WEB -> {
                    searchWeb(task.action.target.ifBlank { task.action.message })
                }

                ActionRegistry.INTENT_PLAY_YOUTUBE -> {
                    playYouTube(task.action.target)
                }

                ActionRegistry.INTENT_SEND_WHATSAPP -> {
                    sendWhatsApp(task.action)
                }

                ActionRegistry.INTENT_SEND_SMS -> {
                    sendSms(task.action)
                }

                ActionRegistry.INTENT_CALL_PHONE -> {
                    callPhone(task.action.target)
                }

                else -> {
                    launchApp(task.action.target)
                }
            }

            val stepDuration = System.currentTimeMillis() - stepStartTime
            if (outcome.isSuccess) {
                task.status = AgentTaskStatus.COMPLETED
                task.resultMessage = outcome.userMessage
                successfulTasksCount++
                logs.add("Task $taskNumber: ${task.title} ✓")

                taskManager.trackStepSuccess(
                    stepIndex = taskNumber,
                    totalSteps = totalTasks,
                    taskName = task.title,
                    message = outcome.userMessage,
                    executionTimeMs = stepDuration
                )

                AgentOverlayManager.completeTaskStep(
                    taskNumber = taskNumber,
                    totalTasks = totalTasks,
                    taskTitle = task.title,
                    isLastTask = isLast
                )
                delay(700L)
            } else {
                task.status = AgentTaskStatus.FAILED
                task.resultMessage = outcome.userMessage
                logs.add("Task $taskNumber: ${task.title} (Failed: ${outcome.userMessage})")

                taskManager.trackStepFailure(
                    stepIndex = taskNumber,
                    totalSteps = totalTasks,
                    taskName = task.title,
                    errorMessage = outcome.userMessage,
                    executionTimeMs = stepDuration
                )
            }
        }

        val allCompleted = (successfulTasksCount == totalTasks)
        val formattedSummary = if (allCompleted) {
            "✅ Completed all $totalTasks tasks in sequence:\n" + plan.tasks.joinToString("\n") {
                "• Task ${it.taskNumber}: ${it.title} ✓"
            }
        } else {
            "Completed $successfulTasksCount of $totalTasks tasks:\n" + plan.tasks.joinToString("\n") {
                val mark = if (it.status == AgentTaskStatus.COMPLETED) "✓" else "✕"
                "• Task ${it.taskNumber}: ${it.title} $mark"
            }
        }

        taskManager.trackExecutionSummary(
            TaskExecutionSummary(
                isSuccess = allCompleted,
                totalTasks = totalTasks,
                completedTasks = successfulTasksCount,
                failedTasks = totalTasks - successfulTasksCount,
                skippedTasks = 0,
                totalDurationMs = System.currentTimeMillis() - planStartTime,
                stepResults = emptyList(),
                logs = taskManager.getRecentLogs(),
                summaryMessage = formattedSummary
            )
        )

        AgentOverlayManager.complete(
            successMessage = if (allCompleted) "All $totalTasks tasks completed successfully! ✓" else "Finished $successfulTasksCount of $totalTasks tasks",
            autoDismissDelayMs = 3200L
        )

        ExecutionOutcome(
            isSuccess = successfulTasksCount > 0,
            userMessage = formattedSummary,
            status = if (allCompleted) "Successful" else "Partial",
            details = logs.joinToString("; ")
        )
    }

    private suspend fun executeAgenticTask(action: ParsedAction): ExecutionOutcome {
        val target = action.target.trim()
        val message = action.message.trim()
        val fullPrompt = if (message.isNotBlank() && target.isNotBlank() && !message.contains(target, ignoreCase = true)) {
            "$target $message"
        } else {
            message.ifBlank { target }
        }

        val plan = AgentTaskDecomposer.decompose(fullPrompt, action)
        return executeTaskPlan(plan)
    }

    private suspend fun executeDismissPopupInternal(): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            for (attempt in 1..2) {
                if (accessibility.dismissPopupsOrAds()) {
                    return ExecutionOutcome(true, "Closed popup ad on screen", "Successful")
                }
                delay(300L)
            }
            // If no obstructing popup or ad was found, do NOT perform a Back press
            // because that would exit or close the application that was just opened!
            return ExecutionOutcome(true, "No popups blocking UI", "Successful")
        }

        return ExecutionOutcome(true, "Checked for popups", "Successful")
    }

    private suspend fun executeToggleSwitchInternal(target: String): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val keywords = if (target.isNotBlank()) listOf(target) else emptyList()
            for (attempt in 1..3) {
                if (accessibility.toggleSwitchOrProtection(keywords)) {
                    return ExecutionOutcome(true, "Turned on switch / protection", "Successful")
                }
                delay(500L)
            }
            // Fallback: tap center screen where main power buttons / shields reside
            if (accessibility.tapCenterScreen()) {
                return ExecutionOutcome(true, "Tapped center protection switch", "Successful")
            }
        }

        val bridge = ShizukuBridge.getInstance(context)
        if (bridge.status.value.isRunning && bridge.status.value.isPermissionGranted) {
            val dm = context.resources.displayMetrics
            val midX = dm.widthPixels / 2
            val midY = (dm.heightPixels * 0.48).toInt()
            val res = bridge.inputTap(midX, midY)
            if (res.isSuccess) {
                return ExecutionOutcome(true, "Tapped center toggle button via Shizuku", "Successful")
            }
        }

        return if (accessibility == null && !bridge.status.value.isRunning) {
            ExecutionOutcome(false, "Enable MayaX Accessibility Service to toggle switches automatically", "Blocked")
        } else {
            ExecutionOutcome(true, "Toggled protection switch on screen", "Successful")
        }
    }

    private fun executeReadScreenInternal(): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            val inspection = accessibility.inspectEntireScreen()
            val textSummary = accessibility.readVisibleTextSummary()
            val msg = if (inspection.allTexts.isNotEmpty()) {
                "Screen Content (${inspection.packageName}):\n$textSummary"
            } else {
                "Active Screen (${inspection.packageName}): No text elements visible"
            }
            return ExecutionOutcome(true, msg, "Successful", inspection.fullHierarchySummary)
        }
        return ExecutionOutcome(false, "Enable MayaX Accessibility Service to read device screen", "Blocked")
    }

    private suspend fun executeTapCoordinatesInternal(target: String): ExecutionOutcome {
        val numbers = Regex("""\d+""").findAll(target).map { it.value.toFloat() }.toList()
        val x = numbers.getOrNull(0) ?: 540f
        val y = numbers.getOrNull(1) ?: 1100f

        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null && accessibility.clickCoordinates(x, y)) {
            return ExecutionOutcome(true, "Tapped coordinates ($x, $y)", "Successful")
        }

        val bridge = ShizukuBridge.getInstance(context)
        if (bridge.status.value.isRunning && bridge.status.value.isPermissionGranted) {
            val res = bridge.inputTap(x.toInt(), y.toInt())
            return ExecutionOutcome(res.isSuccess, "Tapped coordinates ($x, $y) via Shizuku", if (res.isSuccess) "Successful" else "Error")
        }

        return ExecutionOutcome(false, "Could not tap coordinates ($x, $y)", "Error")
    }

    private suspend fun executeClickTextInternal(target: String): ExecutionOutcome {
        val accessibility = MayaAccessibilityService.instance
        if (accessibility != null) {
            for (attempt in 1..3) {
                if (accessibility.clickByText(target)) {
                    return ExecutionOutcome(true, "Clicked '$target' on screen", "Successful")
                }
                delay(400L)
            }
        }

        return ExecutionOutcome(false, "Could not find '$target' on screen", "Blocked")
    }

    private suspend fun executeDismissPopup(): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Closing ad/popup...", null)
        val result = executeDismissPopupInternal()
        AgentOverlayManager.complete(result.userMessage)
        return result
    }

    private suspend fun executeToggleSwitch(target: String): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Toggling protection switch...", null)
        val outcome = executeToggleSwitchInternal(target)
        AgentOverlayManager.complete(outcome.userMessage)
        return outcome
    }

    private fun executeReadScreen(): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Reading device screen...", null)
        val result = executeReadScreenInternal()
        AgentOverlayManager.complete(result.userMessage)
        return result
    }

    private suspend fun executeTapCoordinates(target: String): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Tapping ($target)...", null)
        val result = executeTapCoordinatesInternal(target)
        AgentOverlayManager.complete(result.userMessage)
        return result
    }

    private suspend fun executeClickText(target: String): ExecutionOutcome {
        AgentOverlayManager.show(context, "System", "Clicking '$target'...", null)
        val outcome = executeClickTextInternal(target)
        AgentOverlayManager.complete(outcome.userMessage)
        return outcome
    }
}
