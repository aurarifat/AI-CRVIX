package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.devicecontrol.ActionIntentParser
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.ActionValidator
import com.example.devicecontrol.AgentTaskDecomposer
import com.example.devicecontrol.DelayTask
import com.example.devicecontrol.DeviceControlTask
import com.example.devicecontrol.KeyBackTask
import com.example.devicecontrol.KeyHomeTask
import com.example.devicecontrol.LaunchAppTask
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.ShellCommandTask
import com.example.devicecontrol.TapTask
import com.example.devicecontrol.TaskManager
import com.example.devicecontrol.TaskManagerOptions
import com.example.devicecontrol.TaskStepFeedback
import com.example.devicecontrol.TaskStepStatus
import com.example.devicecontrol.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MayaX AI", appName)
  }

  @Test
  fun `verify action registry contains core device intents`() {
    assertTrue(ActionRegistry.isActionRegistered(ActionRegistry.INTENT_OPEN_APP))
    assertTrue(ActionRegistry.isActionRegistered(ActionRegistry.INTENT_OPEN_YOUTUBE))
    assertTrue(ActionRegistry.isActionRegistered(ActionRegistry.INTENT_OPEN_SETTINGS))
    assertTrue(ActionRegistry.isActionRegistered(ActionRegistry.INTENT_GO_HOME))
    assertTrue(ActionRegistry.isActionRegistered(ActionRegistry.INTENT_DEVICE_INFO))
  }

  @Test
  fun `verify action intent parser parses structured actions`() {
    val sampleText = "Sure, opening YouTube for you! ACTION:{\"intent\":\"OPEN_YOUTUBE\",\"target\":\"\"}"
    val parsed = ActionIntentParser.parse(sampleText)
    assertNotNull(parsed)
    assertEquals("OPEN_YOUTUBE", parsed?.intent)

    val clean = ActionIntentParser.cleanResponseText(sampleText)
    assertEquals("Sure, opening YouTube for you!", clean)
  }

  @Test
  fun `verify action validator blocks unauthorized actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val validator = ActionValidator(context)
    val invalidAction = ParsedAction(intent = "DROP_DATABASE_ROOT", target = "all")
    val res = validator.validate(invalidAction)
    assertTrue(res is ValidationResult.Invalid)
  }

  @Test
  fun `verify TaskManager initializes and parses agent task plan`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val taskManager = TaskManager.getInstance(context)
    assertNotNull(taskManager)

    val plan = AgentTaskDecomposer.decompose("open chrome and go home")
    val tasks = taskManager.fromAgentTaskPlan(plan)
    assertEquals(2, tasks.size)
    assertTrue(tasks[0] is LaunchAppTask)
    assertTrue(tasks[1] is KeyHomeTask)
  }

  @Test
  fun `verify TaskManager executes sequential delay tasks with logging and feedback`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val taskManager = TaskManager(context)

    val feedbackList = mutableListOf<TaskStepFeedback>()
    val taskList = listOf(
        DelayTask(durationMs = 20L, name = "Step 1: Wait"),
        DelayTask(durationMs = 20L, name = "Step 2: Wait")
    )

    val summary = taskManager.executeTasks(
        tasks = taskList,
        options = TaskManagerOptions(showOverlayFeedback = false, autoConnectShizuku = false),
        onFeedback = { feedback ->
            feedbackList.add(feedback)
        }
    )

    // Verify feedback was emitted for all steps (RUNNING + SUCCESS for each step)
    assertTrue("Feedback should be emitted for each step", feedbackList.isNotEmpty())
    assertEquals(2, summary.totalTasks)
    assertEquals(2, summary.completedTasks)
    assertEquals(0, summary.failedTasks)
    assertTrue(summary.isSuccess)

    // Verify logs were recorded
    val logs = taskManager.recentLogs.value
    assertTrue("Logs should be recorded for each step", logs.isNotEmpty())
    assertTrue(logs.any { it.taskName.contains("Step 1: Wait") || it.toFormattedString().contains("Step 1: Wait") })
    assertTrue(logs.any { it.taskName.contains("Step 2: Wait") || it.toFormattedString().contains("Step 2: Wait") })
  }

  @Test
  fun `verify TaskManager handles empty task list gracefully`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val taskManager = TaskManager(context)

    val summary = taskManager.executeTasks(
        tasks = emptyList(),
        options = TaskManagerOptions(showOverlayFeedback = false)
    )

    assertTrue(summary.isSuccess)
    assertEquals(0, summary.totalTasks)
    assertEquals(0, summary.completedTasks)
  }
}

