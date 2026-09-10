package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.devicecontrol.ActionIntentParser
import com.example.devicecontrol.ActionRegistry
import com.example.devicecontrol.ActionValidator
import com.example.devicecontrol.ParsedAction
import com.example.devicecontrol.ValidationResult
import org.junit.Assert.assertEquals
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
}

