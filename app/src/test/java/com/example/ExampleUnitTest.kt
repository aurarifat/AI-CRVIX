package com.example

import com.example.data.ai.OpenRouterProvider
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.AgentTaskDecomposer
import com.example.devicecontrol.ParsedAction
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDecomposeCompoundPrompt() {
    val plan = AgentTaskDecomposer.decompose("open adguard close ads and turn it on")
    assertEquals(3, plan.tasks.size)
    assertEquals(1, plan.tasks[0].taskNumber)
    assertEquals(2, plan.tasks[1].taskNumber)
    assertEquals(3, plan.tasks[2].taskNumber)
    assertEquals("Open Adguard", plan.tasks[0].title)
    assertEquals(ActionRegistry.INTENT_DISMISS_POPUP, plan.tasks[1].action.intent)
    assertEquals(ActionRegistry.INTENT_TOGGLE_SWITCH, plan.tasks[2].action.intent)
  }

  @Test
  fun testDecomposeNumberedTasks() {
    val input = "Task 1: Open Chrome\nTask 2: Search Android news\nTask 3: Go home"
    val plan = AgentTaskDecomposer.decompose(input)
    assertEquals(3, plan.tasks.size)
    assertEquals(1, plan.tasks[0].taskNumber)
    assertEquals(2, plan.tasks[1].taskNumber)
    assertEquals(3, plan.tasks[2].taskNumber)
    assertEquals("Open Chrome", plan.tasks[0].title)
    assertEquals("Search Android news", plan.tasks[1].title)
    assertEquals("Go home", plan.tasks[2].title)
  }

  @Test
  fun knownFreeModels_allHaveFreeSuffixOrFlag() {
    val freeModels = OpenRouterProvider.KNOWN_FREE_MODELS
    assertTrue(freeModels.isNotEmpty())
    freeModels.forEach { model ->
      assertTrue(model.isFree)
      assertTrue(model.id.endsWith(":free") || model.id.contains("free"))
    }
  }

  @Test
  fun testFreeModelSuffixDetection() {
    val testFreeId = "google/gemini-2.0-flash-exp:free"
    val testPaidId = "openai/gpt-4o"
    assertTrue(testFreeId.endsWith(":free"))
    assertFalse(testPaidId.endsWith(":free"))
  }
}
