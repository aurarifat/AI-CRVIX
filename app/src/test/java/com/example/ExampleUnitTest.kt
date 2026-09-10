package com.example

import com.example.data.ai.OpenRouterProvider
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
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
