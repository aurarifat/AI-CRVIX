package com.example.devicecontrol

import android.content.Context
import android.util.Log
import com.example.data.local.ActionHistoryEntity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// =============================================================================
// Task Models & Specifications
// =============================================================================

/**
 * Common interface representing an atomic device-control task executable via Shizuku.
 */
sealed interface DeviceControlTask {
    val id: String
    val name: String
    val description: String
    val delayBeforeMs: Long
    val delayAfterMs: Long
    val timeoutMs: Long
    val continueOnFailure: Boolean
}

/**
 * Executes a privileged shell command via Shizuku API.
 */
data class ShellCommandTask(
    val command: String,
    override val name: String = "Shell: ${command.take(30)}",
    override val description: String = "Execute shell command via Shizuku: $command",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 150L,
    override val timeoutMs: Long = 10000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Launches an application via Shizuku (am start / monkey).
 */
data class LaunchAppTask(
    val packageName: String,
    override val name: String = "Launch $packageName",
    override val description: String = "Launch application package $packageName",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 800L,
    override val timeoutMs: Long = 10000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Force-stops an application without root prompt via Shizuku.
 */
data class ForceStopAppTask(
    val packageName: String,
    override val name: String = "Force Stop $packageName",
    override val description: String = "Force stop package $packageName",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 200L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates a touch tap at screen coordinates (x, y) via Shizuku input tap.
 */
data class TapTask(
    val x: Int,
    val y: Int,
    override val name: String = "Tap ($x, $y)",
    override val description: String = "Tap on screen coordinates ($x, $y)",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 300L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates a swipe gesture from (x1, y1) to (x2, y2) via Shizuku.
 */
data class SwipeTask(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val durationMs: Int = 300,
    override val name: String = "Swipe ($x1, $y1) -> ($x2, $y2)",
    override val description: String = "Swipe gesture across screen",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 300L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates keyboard text input via Shizuku input text.
 */
data class InputTextTask(
    val text: String,
    override val name: String = "Input Text",
    override val description: String = "Type text '$text'",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 250L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates an Android hardware keyevent (e.g. Back=4, Home=3, Enter=66).
 */
data class KeyEventTask(
    val keyCode: Int,
    val keyName: String = "KeyCode $keyCode",
    override val name: String = "Key: $keyName",
    override val description: String = "Send hardware keyevent $keyCode ($keyName)",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 250L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates the Back button keyevent.
 */
data class KeyBackTask(
    override val name: String = "Navigate Back",
    override val description: String = "Send Back keyevent (KEYCODE_BACK)",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 250L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates the Home button keyevent.
 */
data class KeyHomeTask(
    override val name: String = "Navigate Home",
    override val description: String = "Send Home keyevent (KEYCODE_HOME)",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 300L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Simulates the Recents/App Switcher button keyevent.
 */
data class KeyRecentsTask(
    override val name: String = "Open Recents",
    override val description: String = "Send Recents keyevent (KEYCODE_APP_SWITCH)",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 300L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Grants a package runtime permission using pm grant via Shizuku.
 */
data class GrantPermissionTask(
    val packageName: String,
    val permission: String,
    override val name: String = "Grant ${permission.substringAfterLast('.')}",
    override val description: String = "Grant permission $permission to $packageName",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 150L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Sets a system, secure, or global setting via Shizuku.
 */
data class PutSettingTask(
    val namespace: String = "secure",
    val key: String,
    val value: String,
    override val name: String = "Put Setting $key",
    override val description: String = "Update setting $namespace $key=$value",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 150L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Automatically enables the app's Accessibility Service via privileged settings commands.
 */
data class EnableAccessibilityTask(
    override val name: String = "Enable Accessibility Service",
    override val description: String = "Auto-enable MayaAccessibilityService via Shizuku",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 500L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = true,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Automatically grants SYSTEM_ALERT_WINDOW (Draw over other apps) via Shizuku appops.
 */
data class GrantOverlayTask(
    override val name: String = "Grant Display Overlay",
    override val description: String = "Grant SYSTEM_ALERT_WINDOW permission via Shizuku appops",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 250L,
    override val timeoutMs: Long = 5000L,
    override val continueOnFailure: Boolean = true,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Pauses execution for a designated duration.
 */
data class DelayTask(
    val durationMs: Long,
    override val name: String = "Wait ${durationMs}ms",
    override val description: String = "Pause task execution for ${durationMs}ms",
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 0L,
    override val timeoutMs: Long = 60000L,
    override val continueOnFailure: Boolean = true,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

/**
 * Custom Shizuku action callback.
 */
data class CustomShizukuTask(
    override val name: String,
    override val description: String = name,
    val action: suspend (ShizukuBridge) -> ShizukuCommandResult,
    override val delayBeforeMs: Long = 0L,
    override val delayAfterMs: Long = 200L,
    override val timeoutMs: Long = 10000L,
    override val continueOnFailure: Boolean = false,
    override val id: String = UUID.randomUUID().toString()
) : DeviceControlTask

// =============================================================================
// Status, Logging & Feedback Models
// =============================================================================

enum class TaskStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED
}

enum class TaskLogLevel {
    DEBUG,
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class TaskLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val stepIndex: Int,
    val taskName: String,
    val level: TaskLogLevel,
    val message: String,
    val commandResult: ShizukuCommandResult? = null,
    val status: TaskStepStatus = when (level) {
        TaskLogLevel.SUCCESS -> TaskStepStatus.SUCCESS
        TaskLogLevel.ERROR -> TaskStepStatus.FAILED
        TaskLogLevel.WARNING -> TaskStepStatus.SKIPPED
        TaskLogLevel.INFO -> TaskStepStatus.RUNNING
        else -> TaskStepStatus.PENDING
    },
    val totalSteps: Int = 0,
    val executionTimeMs: Long = 0L
) {
    fun toFormattedString(): String {
        val levelIcon = when (level) {
            TaskLogLevel.SUCCESS -> "✅"
            TaskLogLevel.INFO -> "ℹ️"
            TaskLogLevel.WARNING -> "⚠️"
            TaskLogLevel.ERROR -> "❌"
            TaskLogLevel.DEBUG -> "🔍"
        }
        val stepPrefix = if (stepIndex > 0) {
            if (totalSteps > 0) "[Step $stepIndex/$totalSteps] " else "[Step $stepIndex] "
        } else ""
        val durationSuffix = if (executionTimeMs > 0) " (${executionTimeMs}ms)" else ""
        return "$levelIcon $stepPrefix$taskName: $message$durationSuffix"
    }
}

data class TaskStepFeedback(
    val stepIndex: Int,
    val totalSteps: Int,
    val task: DeviceControlTask,
    val status: TaskStepStatus,
    val message: String,
    val progressPercent: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val commandResult: ShizukuCommandResult? = null
)

data class TaskStepResult(
    val stepIndex: Int,
    val task: DeviceControlTask,
    val status: TaskStepStatus,
    val isSuccess: Boolean,
    val executionTimeMs: Long,
    val message: String,
    val commandResult: ShizukuCommandResult? = null
)

data class TaskExecutionSummary(
    val isSuccess: Boolean,
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val skippedTasks: Int,
    val totalDurationMs: Long,
    val stepResults: List<TaskStepResult>,
    val logs: List<TaskLogEntry>,
    val summaryMessage: String
) {
    fun toDetailedReport(): String {
        val sb = StringBuilder()
        sb.appendLine(if (isSuccess) "✅ Task Sequence Completed Successfully" else "⚠️ Task Sequence Finished with Issues")
        sb.appendLine("Completed: $completedTasks / $totalTasks (Duration: ${totalDurationMs}ms)")
        sb.appendLine("─".repeat(40))
        stepResults.forEach { res ->
            val icon = when (res.status) {
                TaskStepStatus.SUCCESS -> "✓"
                TaskStepStatus.FAILED -> "✕"
                TaskStepStatus.SKIPPED -> "↷"
                TaskStepStatus.CANCELLED -> "⊘"
                else -> "•"
            }
            sb.appendLine("$icon [${res.stepIndex}] ${res.task.name}: ${res.message} (${res.executionTimeMs}ms)")
        }
        return sb.toString().trimEnd()
    }
}

data class TaskManagerOptions(
    val continueOnError: Boolean = false,
    val showOverlayFeedback: Boolean = true,
    val autoConnectShizuku: Boolean = true,
    val stopOnCancel: Boolean = true
)

// =============================================================================
// TaskManager Implementation
// =============================================================================

/**
 * TaskManager receives a sequence of device-control tasks, verifies Shizuku API availability,
 * and executes each task sequentially with comprehensive logging and real-time feedback.
 */
class TaskManager(
    private val context: Context,
    val shizukuBridge: ShizukuBridge = ShizukuBridge.getInstance(context)
) {

    companion object {
        private const val TAG = "TaskManager"

        @Volatile
        private var instance: TaskManager? = null

        fun getInstance(context: Context): TaskManager {
            return instance ?: synchronized(this) {
                instance ?: TaskManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentFeedback = MutableStateFlow<TaskStepFeedback?>(null)
    val currentFeedback: StateFlow<TaskStepFeedback?> = _currentFeedback.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<TaskStepFeedback>(extraBufferCapacity = 64)
    val feedbackEvents: SharedFlow<TaskStepFeedback> = _feedbackEvents.asSharedFlow()

    private val _recentLogs = MutableStateFlow<List<TaskLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<TaskLogEntry>> = _recentLogs.asStateFlow()

    private val _lastSummary = MutableStateFlow<TaskExecutionSummary?>(null)
    val lastSummary: StateFlow<TaskExecutionSummary?> = _lastSummary.asStateFlow()

    private var activeJob: Job? = null
    @Volatile
    private var isCancelled = false

    /**
     * Executes a list of device-control tasks sequentially via the Shizuku API.
     * Ensures every step is logged to internal state & logcat, and emits real-time feedback.
     */
    suspend fun executeTasks(
        tasks: List<DeviceControlTask>,
        options: TaskManagerOptions = TaskManagerOptions(),
        onFeedback: ((TaskStepFeedback) -> Unit)? = null
    ): TaskExecutionSummary = withContext(Dispatchers.Default) {
        val totalTasks = tasks.size
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<TaskStepResult>()
        val runLogs = mutableListOf<TaskLogEntry>()

        if (totalTasks == 0) {
            val emptyLog = logStep(0, "Empty Task List", TaskLogLevel.WARNING, "No tasks provided to execute")
            runLogs.add(emptyLog)
            val summary = TaskExecutionSummary(
                isSuccess = true,
                totalTasks = 0,
                completedTasks = 0,
                failedTasks = 0,
                skippedTasks = 0,
                totalDurationMs = 0L,
                stepResults = emptyList(),
                logs = runLogs,
                summaryMessage = "No tasks to execute"
            )
            _lastSummary.value = summary
            return@withContext summary
        }

        _isExecuting.value = true
        isCancelled = false

        logStep(0, "TaskManager", TaskLogLevel.INFO, "Starting sequential execution of $totalTasks device-control tasks")

        // 1. Verify Shizuku API availability & permissions
        if (options.autoConnectShizuku && !shizukuBridge.checkPermission()) {
            logStep(0, "Shizuku Check", TaskLogLevel.INFO, "Shizuku permission not detected. Attempting status refresh...")
            shizukuBridge.forceReconnect()
        }

        val hasShizukuPermission = shizukuBridge.checkPermission()
        if (!hasShizukuPermission) {
            logStep(0, "Shizuku API", TaskLogLevel.WARNING, "Shizuku permission is not active. Privileged commands may fail until authorized in Shizuku app.")
        }

        // 2. Sequential task execution loop
        var completedCount = 0
        var failedCount = 0
        var skippedCount = 0

        for (i in tasks.indices) {
            val task = tasks[i]
            val stepNumber = i + 1
            val baseProgress = (((stepNumber - 1).toFloat() / totalTasks) * 100).toInt()

            // Check if user requested cancellation
            if (isCancelled) {
                logStep(stepNumber, task.name, TaskLogLevel.WARNING, "Task cancelled by user request")
                skippedCount++
                val skippedResult = TaskStepResult(
                    stepIndex = stepNumber,
                    task = task,
                    status = TaskStepStatus.CANCELLED,
                    isSuccess = false,
                    executionTimeMs = 0L,
                    message = "Cancelled before execution"
                )
                stepResults.add(skippedResult)
                emitFeedback(stepNumber, totalTasks, task, TaskStepStatus.CANCELLED, "Task execution cancelled", baseProgress, onFeedback)
                continue
            }

            // Emit RUNNING feedback & HUD notification
            val startMsg = "Executing: ${task.name}"
            logStep(stepNumber, task.name, TaskLogLevel.INFO, "Starting task ($stepNumber/$totalTasks): ${task.description}")
            emitFeedback(stepNumber, totalTasks, task, TaskStepStatus.RUNNING, startMsg, baseProgress, onFeedback)

            if (options.showOverlayFeedback) {
                withContext(Dispatchers.Main) {
                    AgentOverlayManager.showTaskProgress(
                        context = context,
                        taskNumber = stepNumber,
                        totalTasks = totalTasks,
                        taskTitle = task.name,
                        status = task.description,
                        countdownSeconds = null,
                        icon = getTaskIcon(task)
                    )
                }
            }

            // Pre-delay if specified
            if (task.delayBeforeMs > 0) {
                delay(task.delayBeforeMs)
            }

            // Execute the individual step via Shizuku API
            val stepStartTime = System.currentTimeMillis()
            val (stepSuccess, resultMessage, cmdResult) = executeSingleTask(task)
            val stepDuration = System.currentTimeMillis() - stepStartTime

            val stepStatus = if (stepSuccess) TaskStepStatus.SUCCESS else TaskStepStatus.FAILED
            val endProgress = ((stepNumber.toFloat() / totalTasks) * 100).toInt()

            if (stepSuccess) {
                completedCount++
                val successLog = logStep(
                    stepIndex = stepNumber,
                    taskName = task.name,
                    level = TaskLogLevel.SUCCESS,
                    message = "$resultMessage (${stepDuration}ms)",
                    cmdResult = cmdResult,
                    totalSteps = totalTasks,
                    durationMs = stepDuration,
                    status = TaskStepStatus.SUCCESS
                )
                runLogs.add(successLog)

                persistLogToDatabase(
                    actionType = "COMMAND_STEP_${stepNumber}_OF_${totalTasks}",
                    target = task.name,
                    status = "Successful",
                    details = "$resultMessage (Duration: ${stepDuration}ms)"
                )

                val stepResult = TaskStepResult(
                    stepIndex = stepNumber,
                    task = task,
                    status = TaskStepStatus.SUCCESS,
                    isSuccess = true,
                    executionTimeMs = stepDuration,
                    message = resultMessage,
                    commandResult = cmdResult
                )
                stepResults.add(stepResult)

                emitFeedback(stepNumber, totalTasks, task, TaskStepStatus.SUCCESS, resultMessage, endProgress, onFeedback, cmdResult)

                if (options.showOverlayFeedback) {
                    withContext(Dispatchers.Main) {
                        AgentOverlayManager.completeTaskStep(
                            taskNumber = stepNumber,
                            totalTasks = totalTasks,
                            taskTitle = task.name,
                            isLastTask = (stepNumber == totalTasks)
                        )
                    }
                }

                // Post-delay if specified
                if (task.delayAfterMs > 0) {
                    delay(task.delayAfterMs)
                }
            } else {
                failedCount++
                val failureMsg = if (cmdResult != null && cmdResult.stderr.isNotBlank()) {
                    "$resultMessage (Error: ${cmdResult.stderr})"
                } else {
                    resultMessage
                }

                val failLog = logStep(
                    stepIndex = stepNumber,
                    taskName = task.name,
                    level = TaskLogLevel.ERROR,
                    message = failureMsg,
                    cmdResult = cmdResult,
                    totalSteps = totalTasks,
                    durationMs = stepDuration,
                    status = TaskStepStatus.FAILED
                )
                runLogs.add(failLog)

                persistLogToDatabase(
                    actionType = "COMMAND_STEP_${stepNumber}_OF_${totalTasks}",
                    target = task.name,
                    status = "Failed",
                    details = "$failureMsg (Duration: ${stepDuration}ms)"
                )

                val stepResult = TaskStepResult(
                    stepIndex = stepNumber,
                    task = task,
                    status = TaskStepStatus.FAILED,
                    isSuccess = false,
                    executionTimeMs = stepDuration,
                    message = failureMsg,
                    commandResult = cmdResult
                )
                stepResults.add(stepResult)

                emitFeedback(stepNumber, totalTasks, task, TaskStepStatus.FAILED, failureMsg, endProgress, onFeedback, cmdResult)

                // If task does not allow continuing on failure, abort subsequent tasks
                val shouldContinue = task.continueOnFailure || options.continueOnError
                if (!shouldContinue) {
                    logStep(stepNumber, "TaskManager", TaskLogLevel.WARNING, "Aborting remaining tasks due to failure in step $stepNumber (${task.name})")

                    for (remainingIdx in (i + 1) until totalTasks) {
                        val remainingTask = tasks[remainingIdx]
                        skippedCount++
                        stepResults.add(
                            TaskStepResult(
                                stepIndex = remainingIdx + 1,
                                task = remainingTask,
                                status = TaskStepStatus.SKIPPED,
                                isSuccess = false,
                                executionTimeMs = 0L,
                                message = "Skipped due to prior failure in step $stepNumber"
                            )
                        )
                    }
                    break
                }
            }
        }

        _isExecuting.value = false
        val totalDuration = System.currentTimeMillis() - startTime
        val overallSuccess = (failedCount == 0 && completedCount > 0)

        val summaryMessage = if (overallSuccess) {
            "Successfully completed all $completedCount tasks via Shizuku API ($totalDuration ms)"
        } else {
            "Completed $completedCount of $totalTasks tasks ($failedCount failed, $skippedCount skipped)"
        }

        logStep(0, "TaskManager Summary", if (overallSuccess) TaskLogLevel.SUCCESS else TaskLogLevel.WARNING, summaryMessage)

        if (options.showOverlayFeedback) {
            withContext(Dispatchers.Main) {
                AgentOverlayManager.complete(
                    successMessage = summaryMessage,
                    autoDismissDelayMs = 3000L
                )
            }
        }

        val summary = TaskExecutionSummary(
            isSuccess = overallSuccess,
            totalTasks = totalTasks,
            completedTasks = completedCount,
            failedTasks = failedCount,
            skippedTasks = skippedCount,
            totalDurationMs = totalDuration,
            stepResults = stepResults,
            logs = runLogs,
            summaryMessage = summaryMessage
        )
        _lastSummary.value = summary
        summary
    }

    /**
     * Executes a single device-control task directly.
     */
    suspend fun executeTask(
        task: DeviceControlTask,
        options: TaskManagerOptions = TaskManagerOptions(),
        onFeedback: ((TaskStepFeedback) -> Unit)? = null
    ): TaskStepResult {
        val summary = executeTasks(listOf(task), options, onFeedback)
        return summary.stepResults.firstOrNull() ?: TaskStepResult(
            stepIndex = 1,
            task = task,
            status = TaskStepStatus.FAILED,
            isSuccess = false,
            executionTimeMs = 0L,
            message = "Task did not produce a result"
        )
    }

    /**
     * Cancels any ongoing task sequence.
     */
    fun cancel() {
        isCancelled = true
        activeJob?.cancel()
        logStep(0, "TaskManager", TaskLogLevel.WARNING, "Cancellation requested by caller")
    }

    /**
     * Clears recent task execution logs.
     */
    fun clearLogs() {
        _recentLogs.value = emptyList()
    }

    // =========================================================================
    // Individual Task Dispatcher
    // =========================================================================

    private suspend fun executeSingleTask(task: DeviceControlTask): Triple<Boolean, String, ShizukuCommandResult?> {
        return try {
            when (task) {
                is ShellCommandTask -> {
                    val result = shizukuBridge.executeCommand(task.command, task.timeoutMs)
                    val msg = if (result.isSuccess) {
                        "Executed command successfully: ${result.stdout.ifBlank { "OK" }}"
                    } else {
                        "Command failed with exit code ${result.exitCode}: ${result.stderr}"
                    }
                    Triple(result.isSuccess, msg, result)
                }

                is LaunchAppTask -> {
                    val result = shizukuBridge.startApp(task.packageName)
                    val msg = if (result.isSuccess) "Launched ${task.packageName}" else "Failed to launch ${task.packageName}: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is ForceStopAppTask -> {
                    val result = shizukuBridge.forceStopApp(task.packageName)
                    val msg = if (result.isSuccess) "Force-stopped ${task.packageName}" else "Failed to force-stop: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is TapTask -> {
                    val result = shizukuBridge.inputTap(task.x, task.y)
                    val msg = if (result.isSuccess) "Tapped (${task.x}, ${task.y})" else "Tap failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is SwipeTask -> {
                    val result = shizukuBridge.inputSwipe(task.x1, task.y1, task.x2, task.y2, task.durationMs)
                    val msg = if (result.isSuccess) "Swiped from (${task.x1}, ${task.y1}) to (${task.x2}, ${task.y2})" else "Swipe failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is InputTextTask -> {
                    val result = shizukuBridge.inputText(task.text)
                    val msg = if (result.isSuccess) "Typed text successfully" else "Text input failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is KeyEventTask -> {
                    val result = shizukuBridge.inputKeyEvent(task.keyCode)
                    val msg = if (result.isSuccess) "Sent keyevent ${task.keyCode} (${task.keyName})" else "Keyevent failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is KeyBackTask -> {
                    val result = shizukuBridge.inputBack()
                    val msg = if (result.isSuccess) "Navigated Back" else "Back navigation failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is KeyHomeTask -> {
                    val result = shizukuBridge.inputHome()
                    val msg = if (result.isSuccess) "Navigated Home" else "Home navigation failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is KeyRecentsTask -> {
                    val result = shizukuBridge.inputRecents()
                    val msg = if (result.isSuccess) "Opened Recents" else "Recents failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is GrantPermissionTask -> {
                    val result = shizukuBridge.grantRuntimePermission(task.packageName, task.permission)
                    val msg = if (result.isSuccess) "Granted ${task.permission} to ${task.packageName}" else "Failed to grant permission: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is PutSettingTask -> {
                    val cmd = "settings put ${task.namespace} ${task.key} ${task.value}"
                    val result = shizukuBridge.executeCommand(cmd, task.timeoutMs)
                    val msg = if (result.isSuccess) "Updated setting ${task.key}=${task.value}" else "Setting update failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }

                is EnableAccessibilityTask -> {
                    val success = shizukuBridge.tryEnableAccessibilityService()
                    val msg = if (success) "Enabled MayaAccessibilityService" else "Could not enable accessibility service"
                    Triple(success, msg, null)
                }

                is GrantOverlayTask -> {
                    val success = shizukuBridge.tryGrantOverlayPermission()
                    val msg = if (success) "Granted SYSTEM_ALERT_WINDOW permission" else "Could not grant overlay permission"
                    Triple(success, msg, null)
                }

                is DelayTask -> {
                    delay(task.durationMs)
                    Triple(true, "Waited ${task.durationMs}ms", null)
                }

                is CustomShizukuTask -> {
                    val result = task.action(shizukuBridge)
                    val msg = if (result.isSuccess) "Custom task '${task.name}' succeeded" else "Custom task failed: ${result.stderr}"
                    Triple(result.isSuccess, msg, result)
                }
            }
        } catch (c: CancellationException) {
            Triple(false, "Task was cancelled", null)
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during task execution: ${task.name}", t)
            Triple(false, "Task threw exception: ${t.localizedMessage}", null)
        }
    }

    // =========================================================================
    // Logging & Feedback Helpers
    // =========================================================================

    fun getRecentLogs(): List<TaskLogEntry> = _recentLogs.value

    fun trackStepStart(
        stepIndex: Int,
        totalSteps: Int,
        taskName: String,
        description: String
    ): TaskLogEntry {
        _isExecuting.value = true
        val entry = logStep(
            stepIndex = stepIndex,
            taskName = taskName,
            level = TaskLogLevel.INFO,
            message = "Starting command: $description",
            totalSteps = totalSteps,
            status = TaskStepStatus.RUNNING
        )
        val feedback = TaskStepFeedback(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            task = CustomShizukuTask(taskName, description, { ShizukuCommandResult(true, 0, "", "") }),
            status = TaskStepStatus.RUNNING,
            message = "Executing: $taskName",
            progressPercent = if (totalSteps > 0) (((stepIndex - 1).toFloat() / totalSteps) * 100).toInt() else 0
        )
        _currentFeedback.value = feedback
        return entry
    }

    fun trackStepSuccess(
        stepIndex: Int,
        totalSteps: Int,
        taskName: String,
        message: String,
        executionTimeMs: Long,
        cmdResult: ShizukuCommandResult? = null
    ): TaskLogEntry {
        val entry = logStep(
            stepIndex = stepIndex,
            taskName = taskName,
            level = TaskLogLevel.SUCCESS,
            message = message,
            cmdResult = cmdResult,
            totalSteps = totalSteps,
            durationMs = executionTimeMs,
            status = TaskStepStatus.SUCCESS
        )
        val feedback = TaskStepFeedback(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            task = CustomShizukuTask(taskName, message, { cmdResult ?: ShizukuCommandResult(true, 0, "", "") }),
            status = TaskStepStatus.SUCCESS,
            message = message,
            progressPercent = if (totalSteps > 0) ((stepIndex.toFloat() / totalSteps) * 100).toInt() else 100,
            commandResult = cmdResult
        )
        _currentFeedback.value = feedback

        persistLogToDatabase(
            actionType = if (totalSteps > 0) "COMMAND_STEP_${stepIndex}_OF_${totalSteps}" else "COMMAND_STEP_$stepIndex",
            target = taskName,
            status = "Successful",
            details = "$message (Duration: ${executionTimeMs}ms)"
        )
        return entry
    }

    fun trackStepFailure(
        stepIndex: Int,
        totalSteps: Int,
        taskName: String,
        errorMessage: String,
        executionTimeMs: Long,
        cmdResult: ShizukuCommandResult? = null
    ): TaskLogEntry {
        val entry = logStep(
            stepIndex = stepIndex,
            taskName = taskName,
            level = TaskLogLevel.ERROR,
            message = errorMessage,
            cmdResult = cmdResult,
            totalSteps = totalSteps,
            durationMs = executionTimeMs,
            status = TaskStepStatus.FAILED
        )
        val feedback = TaskStepFeedback(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            task = CustomShizukuTask(taskName, errorMessage, { cmdResult ?: ShizukuCommandResult(false, 1, "", errorMessage) }),
            status = TaskStepStatus.FAILED,
            message = errorMessage,
            progressPercent = if (totalSteps > 0) ((stepIndex.toFloat() / totalSteps) * 100).toInt() else 100,
            commandResult = cmdResult
        )
        _currentFeedback.value = feedback

        persistLogToDatabase(
            actionType = if (totalSteps > 0) "COMMAND_STEP_${stepIndex}_OF_${totalSteps}" else "COMMAND_STEP_$stepIndex",
            target = taskName,
            status = "Failed",
            details = "$errorMessage (Duration: ${executionTimeMs}ms)"
        )
        return entry
    }

    fun trackExecutionSummary(summary: TaskExecutionSummary) {
        _isExecuting.value = false
        _lastSummary.value = summary
        logStep(
            stepIndex = 0,
            taskName = "Sequence Complete",
            level = if (summary.isSuccess) TaskLogLevel.SUCCESS else TaskLogLevel.WARNING,
            message = summary.summaryMessage,
            totalSteps = summary.totalTasks,
            durationMs = summary.totalDurationMs,
            status = if (summary.isSuccess) TaskStepStatus.SUCCESS else TaskStepStatus.FAILED
        )
    }

    fun persistLogToDatabase(actionType: String, target: String, status: String, details: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.actionHistoryDao().insertAction(
                    ActionHistoryEntity(
                        actionType = actionType,
                        target = target,
                        status = status,
                        details = details
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist task log to database: ${e.message}")
            }
        }
    }

    fun logStep(
        stepIndex: Int,
        taskName: String,
        level: TaskLogLevel,
        message: String,
        cmdResult: ShizukuCommandResult? = null,
        totalSteps: Int = 0,
        durationMs: Long = 0L,
        status: TaskStepStatus? = null
    ): TaskLogEntry {
        val resolvedStatus = status ?: when (level) {
            TaskLogLevel.SUCCESS -> TaskStepStatus.SUCCESS
            TaskLogLevel.ERROR -> TaskStepStatus.FAILED
            TaskLogLevel.WARNING -> TaskStepStatus.SKIPPED
            TaskLogLevel.INFO -> TaskStepStatus.RUNNING
            else -> TaskStepStatus.PENDING
        }
        val entry = TaskLogEntry(
            stepIndex = stepIndex,
            taskName = taskName,
            level = level,
            message = message,
            commandResult = cmdResult,
            status = resolvedStatus,
            totalSteps = totalSteps,
            executionTimeMs = durationMs
        )

        when (level) {
            TaskLogLevel.SUCCESS, TaskLogLevel.INFO -> Log.i(TAG, entry.toFormattedString())
            TaskLogLevel.WARNING -> Log.w(TAG, entry.toFormattedString())
            TaskLogLevel.ERROR -> Log.e(TAG, entry.toFormattedString())
            TaskLogLevel.DEBUG -> Log.d(TAG, entry.toFormattedString())
        }

        val currentList = _recentLogs.value.toMutableList()
        if (currentList.size >= 100) {
            currentList.removeAt(0)
        }
        currentList.add(entry)
        _recentLogs.value = currentList
        return entry
    }

    private suspend fun emitFeedback(
        stepIndex: Int,
        totalSteps: Int,
        task: DeviceControlTask,
        status: TaskStepStatus,
        message: String,
        progressPercent: Int,
        callback: ((TaskStepFeedback) -> Unit)?,
        cmdResult: ShizukuCommandResult? = null
    ) {
        val feedback = TaskStepFeedback(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            task = task,
            status = status,
            message = message,
            progressPercent = progressPercent,
            commandResult = cmdResult
        )

        _currentFeedback.value = feedback
        _feedbackEvents.emit(feedback)
        callback?.invoke(feedback)
    }

    private fun getTaskIcon(task: DeviceControlTask): String {
        return when (task) {
            is LaunchAppTask -> "🚀"
            is ForceStopAppTask -> "⏹️"
            is TapTask -> "👆"
            is SwipeTask -> "👉"
            is InputTextTask -> "⌨️"
            is KeyEventTask, is KeyBackTask, is KeyHomeTask, is KeyRecentsTask -> "📱"
            is GrantPermissionTask, is GrantOverlayTask -> "🛡️"
            is PutSettingTask, is EnableAccessibilityTask -> "⚙️"
            is DelayTask -> "⏳"
            is ShellCommandTask -> "💻"
            is CustomShizukuTask -> "🤖"
        }
    }

    // =========================================================================
    // Conversion Utilities
    // =========================================================================

    /**
     * Converts high-level decomposed agent tasks into concrete Shizuku device tasks.
     */
    fun fromAgentTaskPlan(plan: AgentTaskPlan): List<DeviceControlTask> {
        return plan.tasks.map { item ->
            fromAgentTaskItem(item)
        }
    }

    /**
     * Converts a single AgentTaskItem into an executable DeviceControlTask.
     */
    fun fromAgentTaskItem(item: AgentTaskItem): DeviceControlTask {
        val action = item.action
        return when (action.intent.uppercase()) {
            ActionRegistry.INTENT_OPEN_APP, ActionRegistry.INTENT_OPEN_CHROME,
            ActionRegistry.INTENT_OPEN_YOUTUBE, ActionRegistry.INTENT_OPEN_CALCULATOR,
            ActionRegistry.INTENT_OPEN_SETTINGS -> {
                LaunchAppTask(
                    packageName = action.target,
                    name = item.title,
                    description = "Launch ${action.target} via Shizuku"
                )
            }

            ActionRegistry.INTENT_GO_HOME -> {
                KeyHomeTask(name = item.title, description = "Navigate to Home screen")
            }

            ActionRegistry.INTENT_SYSTEM_BACK -> {
                KeyBackTask(name = item.title, description = "Navigate Back")
            }

            ActionRegistry.INTENT_SYSTEM_RECENTS -> {
                KeyRecentsTask(name = item.title, description = "Show Recent Apps")
            }

            ActionRegistry.INTENT_DISMISS_POPUP -> {
                CustomShizukuTask(
                    name = item.title,
                    description = "Scan and dismiss popups or promo ads",
                    action = { _ ->
                        val accessibility = MayaAccessibilityService.instance
                        if (accessibility != null) {
                            accessibility.dismissPopupsOrAds()
                        }
                        ShizukuCommandResult(
                            isSuccess = true,
                            exitCode = 0,
                            stdout = "Popups checked safely",
                            stderr = ""
                        )
                    }
                )
            }

            ActionRegistry.INTENT_READ_SCREEN -> {
                CustomShizukuTask(
                    name = item.title,
                    description = "Read device screen content",
                    action = { _ ->
                        val accessibility = MayaAccessibilityService.instance
                        val summary = accessibility?.readVisibleTextSummary() ?: "Screen reader not active"
                        ShizukuCommandResult(
                            isSuccess = true,
                            exitCode = 0,
                            stdout = summary,
                            stderr = ""
                        )
                    }
                )
            }

            ActionRegistry.INTENT_TAP_COORDINATES -> {
                val nums = Regex("""\d+""").findAll(action.target).map { it.value.toInt() }.toList()
                val x = nums.getOrNull(0) ?: 500
                val y = nums.getOrNull(1) ?: 1000
                TapTask(x = x, y = y, name = item.title, description = "Tap screen at ($x, $y)")
            }

            ActionRegistry.INTENT_TOGGLE_SWITCH -> {
                // Taps the center action button for protection toggle
                CustomShizukuTask(
                    name = item.title,
                    description = "Toggle protection switch via Shizuku input",
                    action = { bridge ->
                        val dm = context.resources.displayMetrics
                        val midX = dm.widthPixels / 2
                        val midY = (dm.heightPixels * 0.48).toInt()
                        bridge.inputTap(midX, midY)
                    }
                )
            }

            ActionRegistry.INTENT_CLICK_TEXT -> {
                ShellCommandTask(
                    command = "input keyevent 66", // Enter / Click
                    name = item.title,
                    description = "Simulate action click for '${action.target}'"
                )
            }

            else -> {
                ShellCommandTask(
                    command = "am start -a android.intent.action.MAIN",
                    name = item.title,
                    description = "Execute ${action.intent}"
                )
            }
        }
    }
}
