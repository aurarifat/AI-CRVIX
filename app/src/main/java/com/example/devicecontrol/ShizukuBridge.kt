package com.example.devicecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Result data holder for shell commands executed through Shizuku.
 */
data class ShizukuCommandResult(
    val isSuccess: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTimeMs: Long = 0L
)

/**
 * Connection states representing the Shizuku bridge lifecycle.
 */
enum class ShizukuConnectionState {
    NOT_INSTALLED,
    SERVICE_STOPPED,
    CONNECTED_UNAUTHORIZED,
    CONNECTED_AUTHORIZED,
    DEAD
}

/**
 * Detailed status summary for Shizuku bridge.
 */
data class ShizukuBridgeStatus(
    val state: ShizukuConnectionState,
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isPermissionGranted: Boolean,
    val version: Int = 0,
    val uid: Int = -1,
    val summary: String = ""
)

/**
 * ShizukuBridge handles initialization, connection lifecycle, permission negotiation,
 * and privileged system-level automation commands through the Shizuku API.
 */
class ShizukuBridge(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuBridge"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_PERMISSION = 7789

        @Volatile
        private var instance: ShizukuBridge? = null

        fun getInstance(context: Context): ShizukuBridge {
            return instance ?: synchronized(this) {
                instance ?: ShizukuBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _status = MutableStateFlow(
        ShizukuBridgeStatus(
            state = ShizukuConnectionState.NOT_INSTALLED,
            isInstalled = false,
            isRunning = false,
            isPermissionGranted = false,
            summary = "Initializing Shizuku Bridge..."
        )
    )
    val status: StateFlow<ShizukuBridgeStatus> = _status.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private var isInitialized = false

    // Shizuku API listeners
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku Binder received successfully.")
        appendLog("Binder received: connection established")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku Binder died.")
        appendLog("Binder died: connection lost")
        _status.value = ShizukuBridgeStatus(
            state = ShizukuConnectionState.DEAD,
            isInstalled = isShizukuInstalled(),
            isRunning = false,
            isPermissionGranted = false,
            summary = "Shizuku service stopped or crashed"
        )
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Shizuku permission request result: granted=$granted")
            appendLog("Permission result: ${if (granted) "Granted" else "Denied"}")
            refreshStatus()
        }
    }

    /**
     * Initializes the Shizuku bridge, registers event listeners, and evaluates
     * the initial connection state.
     */
    fun initialize() {
        if (isInitialized) {
            refreshStatus()
            return
        }

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            isInitialized = true
            appendLog("Shizuku bridge listeners registered")
            refreshStatus()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize Shizuku bridge", e)
            appendLog("Initialization error: ${e.message}")
            _status.value = evaluateFallbackStatus()
        }
    }

    /**
     * Unregisters listeners and cleans up the bridge resources.
     */
    fun destroy() {
        if (!isInitialized) return
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Error while removing Shizuku listeners", e)
        } finally {
            isInitialized = false
        }
    }

    /**
     * Re-evaluates connection and permission states.
     */
    fun refreshStatus(): ShizukuBridgeStatus {
        val isAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        if (isAlive) {
            val hasPermission = checkPermission()
            val version = try { Shizuku.getVersion() } catch (_: Throwable) { 0 }
            val uid = try { Shizuku.getUid() } catch (_: Throwable) { -1 }

            val state = if (hasPermission) {
                ShizukuConnectionState.CONNECTED_AUTHORIZED
            } else {
                ShizukuConnectionState.CONNECTED_UNAUTHORIZED
            }

            val summary = if (hasPermission) {
                "Connected & Authorized (v$version, UID $uid). Privileged system automation active."
            } else {
                "Connected to Shizuku service (v$version). Permission required — tap Authorize or enable MayaX AI in Shizuku."
            }

            val newStatus = ShizukuBridgeStatus(
                state = state,
                isInstalled = true,
                isRunning = true,
                isPermissionGranted = hasPermission,
                version = version,
                uid = uid,
                summary = summary
            )
            _status.value = newStatus
            return newStatus
        }

        val installed = isShizukuInstalled()
        if (!installed) {
            val newStatus = ShizukuBridgeStatus(
                state = ShizukuConnectionState.NOT_INSTALLED,
                isInstalled = false,
                isRunning = false,
                isPermissionGranted = false,
                summary = "Shizuku is not installed on this device."
            )
            _status.value = newStatus
            return newStatus
        }

        val newStatus = ShizukuBridgeStatus(
            state = ShizukuConnectionState.SERVICE_STOPPED,
            isInstalled = true,
            isRunning = false,
            isPermissionGranted = false,
            summary = "Shizuku is installed, but service is not running. Start it in Shizuku app (via Wireless Debugging or Root)."
        )
        _status.value = newStatus
        return newStatus
    }

    /**
     * Forces re-registration of Shizuku listeners and re-pings the binder.
     */
    fun forceReconnect(): ShizukuBridgeStatus {
        try {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            } catch (_: Throwable) {}

            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            isInitialized = true
            appendLog("Reconnected Shizuku listeners")
        } catch (e: Throwable) {
            Log.w(TAG, "forceReconnect error", e)
            appendLog("Reconnect error: ${e.message}")
        }
        return refreshStatus()
    }

    /**
     * Checks if Shizuku application is installed.
     */
    fun isShizukuInstalled(): Boolean {
        try {
            if (Shizuku.pingBinder()) return true
        } catch (_: Throwable) {}

        val knownPackages = listOf(
            SHIZUKU_PACKAGE,
            "moe.shizuku.privileged.api.debug",
            "rikka.sui"
        )
        val pm = context.packageManager
        for (pkg in knownPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Throwable) {}
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if Shizuku permission has been granted to this app.
     */
    fun checkPermission(): Boolean {
        return try {
            val ping = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
            val grantedViaSdk = if (ping) {
                try {
                    if (Shizuku.isPreV11()) false else (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                } catch (_: Throwable) {
                    false
                }
            } else false

            val grantedViaV23 = context.checkCallingOrSelfPermission(ShizukuManager.SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
            val grantedViaApi = context.checkCallingOrSelfPermission("moe.shizuku.manager.permission.API") == PackageManager.PERMISSION_GRANTED
            val grantedViaSystem = grantedViaV23 || grantedViaApi

            grantedViaSdk || (ping && grantedViaSystem)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Requests Shizuku authorization for system automation.
     */
    fun requestPermission(requestCode: Int = REQUEST_CODE_PERMISSION): Boolean {
        return try {
            val ping = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
            if (!ping) {
                appendLog("Shizuku binder is not connected. Opening Shizuku...")
                openShizukuApp()
                return false
            }
            if (checkPermission()) {
                appendLog("Permission is already granted")
                return true
            }
            if (Shizuku.isPreV11()) {
                appendLog("Pre-v11 Shizuku detected; request through app UI")
                openShizukuApp()
                false
            } else {
                appendLog("Requesting Shizuku permission dialog...")
                Shizuku.requestPermission(requestCode)
                true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            appendLog("Request permission error: ${e.message}")
            openShizukuApp()
            false
        }
    }

    /**
     * Launches the Shizuku Manager application.
     */
    fun openShizukuApp(): Boolean {
        val knownPackages = listOf(
            SHIZUKU_PACKAGE,
            "moe.shizuku.privileged.api.debug",
            "rikka.sui"
        )
        val pm = context.packageManager
        for (pkg in knownPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    /**
     * Directs user to Android Developer Options / Wireless Debugging settings.
     */
    fun openWirelessDebuggingSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Opens Google Play Store page for Shizuku.
     */
    fun openPlayStoreForShizuku(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // =========================================================================
    // Privileged System Automation APIs
    // =========================================================================

    /**
     * Executes an arbitrary shell command with privileged Shizuku permissions.
     */
    suspend fun executeCommand(command: String, timeoutMs: Long = 10000L): ShizukuCommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (!checkPermission()) {
            return@withContext ShizukuCommandResult(
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku permission not granted or service unavailable.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            val process = createProcess(arrayOf("sh", "-c", command))
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read output streams asynchronously
            val stdoutThread = Thread {
                try {
                    var line: String?
                    while (stdoutReader.readLine().also { line = it } != null) {
                        stdoutBuilder.appendLine(line)
                    }
                } catch (_: Exception) {}
            }
            val stderrThread = Thread {
                try {
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        stderrBuilder.appendLine(line)
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }

            if (!finished) {
                process.destroy()
                return@withContext ShizukuCommandResult(
                    isSuccess = false,
                    exitCode = -2,
                    stdout = stdoutBuilder.toString().trim(),
                    stderr = "Command timed out after ${timeoutMs}ms",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            val exitCode = process.exitValue()
            val result = ShizukuCommandResult(
                isSuccess = exitCode == 0,
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trim(),
                stderr = stderrBuilder.toString().trim(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
            appendLog("Exec [$command] -> exit $exitCode")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to execute command via Shizuku: $command", e)
            appendLog("Exec failed: ${e.message}")
            ShizukuCommandResult(
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = e.localizedMessage ?: "Unknown execution error",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Simulates hardware key event (e.g. Back = 4, Home = 3, Recents = 187).
     */
    suspend fun inputKeyEvent(keyCode: Int): ShizukuCommandResult {
        return executeCommand("input keyevent $keyCode")
    }

    suspend fun inputBack(): ShizukuCommandResult = inputKeyEvent(4)
    suspend fun inputHome(): ShizukuCommandResult = inputKeyEvent(3)
    suspend fun inputRecents(): ShizukuCommandResult = inputKeyEvent(187)

    /**
     * Simulates screen tap at coordinates (x, y).
     */
    suspend fun inputTap(x: Int, y: Int): ShizukuCommandResult {
        return executeCommand("input tap $x $y")
    }

    /**
     * Simulates swipe gesture from (x1, y1) to (x2, y2).
     */
    suspend fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): ShizukuCommandResult {
        return executeCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")
    }

    /**
     * Simulates keyboard text input.
     */
    suspend fun inputText(text: String): ShizukuCommandResult {
        val sanitized = text.replace(" ", "%s").replace("'", "\\'")
        return executeCommand("input text '$sanitized'")
    }

    /**
     * Force stops an application by package name without root requirement.
     */
    suspend fun forceStopApp(packageName: String): ShizukuCommandResult {
        return executeCommand("am force-stop $packageName")
    }

    /**
     * Launches an app by package name.
     */
    suspend fun startApp(packageName: String): ShizukuCommandResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val component = launchIntent?.component?.flattenToString()
        return if (component != null) {
            executeCommand("am start -n $component")
        } else {
            executeCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        }
    }

    /**
     * Grants a runtime permission to any package using privileged package manager.
     */
    suspend fun grantRuntimePermission(packageName: String, permission: String): ShizukuCommandResult {
        return executeCommand("pm grant $packageName $permission")
    }

    /**
     * Reads a system property via getprop.
     */
    suspend fun getSystemProperty(propName: String): String {
        val res = executeCommand("getprop $propName")
        return if (res.isSuccess) res.stdout.trim() else ""
    }

    /**
     * Enables MayaAccessibilityService automatically using privileged ADB settings commands.
     */
    suspend fun tryEnableAccessibilityService(): Boolean {
        if (!checkPermission()) return false
        val serviceComponent = "${context.packageName}/com.example.devicecontrol.MayaAccessibilityService"
        return try {
            val res = executeCommand("settings get secure enabled_accessibility_services")
            val current = res.stdout.trim()
            val newServices = if (current.isBlank() || current == "null") {
                serviceComponent
            } else if (!current.contains(serviceComponent)) {
                "$current:$serviceComponent"
            } else {
                current
            }
            executeCommand("settings put secure enabled_accessibility_services $newServices")
            executeCommand("settings put secure accessibility_enabled 1")
            appendLog("Auto-enabled MayaAccessibilityService via Shizuku")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to enable accessibility service via Shizuku: ${e.message}")
            false
        }
    }

    /**
     * Grants SYSTEM_ALERT_WINDOW (Display over other apps) permission via Shizuku appops.
     */
    suspend fun tryGrantOverlayPermission(): Boolean {
        if (!checkPermission()) return false
        return try {
            executeCommand("cmd appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            executeCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            appendLog("Granted SYSTEM_ALERT_WINDOW via Shizuku appops")
            Settings.canDrawOverlays(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to grant overlay permission via Shizuku: ${e.message}")
            false
        }
    }

    /**
     * Full device access setup via Shizuku:
     * grants overlay, enables accessibility service, and grants essential runtime permissions.
     */
    suspend fun grantAllPrivileges(): Boolean {
        if (!checkPermission()) return false
        val pkg = context.packageName
        return try {
            executeCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            executeCommand("pm grant $pkg android.permission.RECORD_AUDIO")
            executeCommand("pm grant $pkg android.permission.READ_CONTACTS")
            executeCommand("pm grant $pkg android.permission.SEND_SMS")
            executeCommand("pm grant $pkg android.permission.CALL_PHONE")
            tryGrantOverlayPermission()
            tryEnableAccessibilityService()
            appendLog("All privileged device permissions granted via Shizuku")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to grant all privileges: ${e.message}")
            false
        }
    }

    private fun createProcess(cmd: Array<String>): Process {
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            newProcessMethod.invoke(null, cmd, null, null) as Process
        } catch (t: Throwable) {
            Log.w(TAG, "Reflection for Shizuku.newProcess failed, falling back to local exec: ${t.message}")
            Runtime.getRuntime().exec(cmd)
        }
    }

    private fun appendLog(message: String) {
        val current = _recentLogs.value.toMutableList()
        if (current.size >= 50) {
            current.removeAt(0)
        }
        current.add("[${System.currentTimeMillis() % 100000}] $message")
        _recentLogs.value = current
    }

    private fun evaluateFallbackStatus(): ShizukuBridgeStatus {
        val installed = isShizukuInstalled()
        return ShizukuBridgeStatus(
            state = if (installed) ShizukuConnectionState.SERVICE_STOPPED else ShizukuConnectionState.NOT_INSTALLED,
            isInstalled = installed,
            isRunning = false,
            isPermissionGranted = false,
            summary = if (installed) "Service not running." else "Shizuku not installed."
        )
    }
}
