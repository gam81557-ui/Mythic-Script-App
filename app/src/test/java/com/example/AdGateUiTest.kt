package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.UnityAdGateState
import com.example.ui.components.AdGateLoadingDialog
import com.example.ui.theme.MythicScriptTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class AdGateUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `gate dialog shows the blocking glass card and countdown`() {
        composeRule.setContent {
            MythicScriptTheme {
                AdGateLoadingDialog(
                    state = UnityAdGateState(
                        visible = true,
                        secondsRemaining = 15,
                        totalSeconds = 15,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("ad_gate_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_gate_countdown").assertIsDisplayed()
        composeRule.onNodeWithTag("ad_gate_seconds").assertTextEquals("15")
    }
}
