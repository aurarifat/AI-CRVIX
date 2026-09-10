package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.MayaXAnimatedOrb
import com.example.ui.theme.MayaXTheme
import com.example.voice.VoiceState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mayax_orb_screenshot() {
    composeTestRule.setContent {
      MayaXTheme {
        MayaXAnimatedOrb(voiceState = VoiceState.IDLE)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/mayax_orb.png")
  }
}

