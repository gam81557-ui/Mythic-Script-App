package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassButtonStyle
import com.example.ui.components.GlassPillButton
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.theme.MythicScriptTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiquidGlassComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `glass panel renders its content above the liquid background`() {
        composeRule.setContent {
            MythicScriptTheme(darkTheme = false) {
                LiquidGlassBackground {
                    LiquidGlassPanel(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .testTag("glass_panel"),
                    ) {
                        Text("Glass content", modifier = Modifier.testTag("glass_content"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("glass_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("glass_content").assertIsDisplayed()
    }

    @Test
    fun `glass pill button reports clicks when enabled and ignores them when disabled`() {
        var enabledClicks = 0
        var disabledClicks = 0

        composeRule.setContent {
            MythicScriptTheme(darkTheme = false) {
                Column {
                    GlassPillButton(
                        text = "Enabled",
                        onClick = { enabledClicks++ },
                        style = GlassButtonStyle.Primary,
                        testTag = "glass_enabled_button",
                    )
                    GlassPillButton(
                        text = "Disabled",
                        onClick = { disabledClicks++ },
                        enabled = false,
                        style = GlassButtonStyle.Neutral,
                        testTag = "glass_disabled_button",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("glass_enabled_button").performClick()
        composeRule.onNodeWithTag("glass_disabled_button").assertHasNoClickAction()

        assertEquals(1, enabledClicks)
        assertEquals(0, disabledClicks)
    }
}
