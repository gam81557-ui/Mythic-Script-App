package com.example

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.components.ShizukuGuideDialog
import com.example.ui.components.ShizukuStatusPill
import com.example.ui.theme.MythicScriptTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShizukuGuideDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `guide exposes open, tutorial, credit, refresh, and close actions`() {
        var openClicks = 0
        var tutorialClicks = 0
        var refreshClicks = 0
        var closeClicks = 0

        composeRule.setContent {
            MythicScriptTheme(darkTheme = true) {
                ShizukuGuideDialog(
                    state = ShizukuConnectionState.NotRunning,
                    onOpenShizukuApp = { openClicks++ },
                    onWatchTutorial = { tutorialClicks++ },
                    onRefresh = { refreshClicks++ },
                    onDismiss = { closeClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("shizuku_guide_title").assertIsDisplayed()
        composeRule.onNodeWithTag("shizuku_guide_message").assertIsDisplayed()
        composeRule.onNodeWithText("Credit: Kazeno").assertIsDisplayed()
        composeRule.onNodeWithTag("shizuku_open_app_button").performClick()
        composeRule.onNodeWithTag("shizuku_watch_tutorial_button").performClick()
        composeRule.onNodeWithTag("shizuku_guide_refresh").performClick()
        composeRule.onNodeWithTag("shizuku_guide_close").performClick()

        assertEquals(1, openClicks)
        assertEquals(1, tutorialClicks)
        assertEquals(1, refreshClicks)
        assertEquals(1, closeClicks)
    }

    @Test
    fun `not installed guide shows the safe fallback notice`() {
        composeRule.setContent {
            MythicScriptTheme(darkTheme = true) {
                ShizukuGuideDialog(
                    state = ShizukuConnectionState.NotInstalled,
                    onOpenShizukuApp = {},
                    onWatchTutorial = {},
                    onRefresh = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("shizuku_not_installed_fallback")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("shizuku_open_app_button").assertIsDisplayed()
        composeRule.onNodeWithText("Credit: Kazeno").assertIsDisplayed()
    }

    @Test
    fun `status pill is exposed as a clickable action`() {
        var clicks = 0
        composeRule.setContent {
            MythicScriptTheme(darkTheme = true) {
                ShizukuStatusPill(
                    state = ShizukuConnectionState.NotRunning,
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("shizuku_status_pill")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(1, clicks)
    }
}
