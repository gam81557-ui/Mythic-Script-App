package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RemoteConfigManager
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.theme.MythicScriptTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class RemoteNoticeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun config(
        id: String,
        title: String,
        message: String,
        fetchedAtMillis: Long = 1L,
    ) = RemoteConfigManager().parseConfigJson(
        """
        {
          "maintenance": false,
          "show_notice": true,
          "notice_id": "$id",
          "title": "$title",
          "message": "$message"
        }
        """.trimIndent(),
        fetchedAtMillis = fetchedAtMillis,
    ).getOrThrow()

    private fun launchApp(): CompanionViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CompanionViewModel(app)
        composeRule.setContent {
            MythicScriptTheme(dynamicColor = false) {
                MythicScriptApp(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun waitForNotice() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Notification", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasTestTag("notice_dialog_title"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoNotice() {
        assertTrue(
            composeRule.onAllNodes(hasTestTag("remote_notice_dialog"))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `show_notice displays dynamic text updates and explicit false hides it`() {
        val viewModel = launchApp()
        val first = config(
            id = "notice_ui_${System.nanoTime()}",
            title = "Notification",
            message = "First release notes",
        )

        viewModel.applyRemoteConfigForTest(first)
        waitForNotice()
        composeRule.onNodeWithTag("notice_dialog_title").assertTextEquals("Notification")
        composeRule.onNodeWithTag("notice_dialog_message").assertTextEquals("First release notes")
        composeRule.onNodeWithTag("notice_info_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("notice_got_it_button").assertIsDisplayed()
        composeRule.onNodeWithTag("notice_close_button").assertIsDisplayed()

        val changedText = config(
            id = first.noticeId,
            title = "Updated Notice",
            message = "Updated release notes",
            fetchedAtMillis = 2L,
        )
        viewModel.applyRemoteConfigForTest(changedText)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Updated Notice")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notice_dialog_message").assertTextEquals("Updated release notes")

        val disabled = RemoteConfigManager().parseConfigJson(
            """{ "maintenance": false, "show_notice": false, "message": "Hidden notice" }""",
            fetchedAtMillis = 3L,
        ).getOrThrow()
        viewModel.applyRemoteConfigForTest(disabled)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("remote_notice_dialog"))
                .fetchSemanticsNodes().isEmpty()
        }
        assertNoNotice()

        viewModel.applyRemoteConfigForTest(first)
        waitForNotice()
        composeRule.onNodeWithTag("notice_dialog_message").assertTextEquals("First release notes")
    }

    @Test
    fun `dismissed notice stays hidden until its id or text changes`() {
        val viewModel = launchApp()
        val first = config(
            id = "notice_dismiss_${System.nanoTime()}",
            title = "Notification",
            message = "Dismiss me",
        )

        viewModel.applyRemoteConfigForTest(first)
        waitForNotice()
        composeRule.onNodeWithTag("notice_got_it_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("remote_notice_dialog"))
                .fetchSemanticsNodes().isEmpty()
        }

        viewModel.applyRemoteConfigForTest(first.copy(fetchedAtMillis = 2L))
        composeRule.waitForIdle()
        assertNoNotice()

        val changedId = config(
            id = "${first.noticeId}_next",
            title = "Notification",
            message = "Dismiss me",
            fetchedAtMillis = 3L,
        )
        viewModel.applyRemoteConfigForTest(changedId)
        waitForNotice()
        composeRule.onNodeWithTag("notice_dialog_message").assertTextEquals("Dismiss me")
    }

    @Test
    fun `maintenance true still shows notice when show_notice is false`() {
        val viewModel = launchApp()
        val maintenance = RemoteConfigManager().parseConfigJson(
            """
            {
              "maintenance": true,
              "show_notice": false,
              "maintenance_title": "Server Maintenance",
              "maintenance_message": "Please wait while we patch the server."
            }
            """.trimIndent(),
            fetchedAtMillis = 10L,
        ).getOrThrow()

        viewModel.applyRemoteConfigForTest(maintenance)
        waitForNotice()
        composeRule.onNodeWithTag("notice_dialog_title").assertTextEquals("Server Maintenance")
        composeRule.onNodeWithTag("notice_dialog_message")
            .assertTextEquals("Please wait while we patch the server.")
        composeRule.onNodeWithTag("notice_close_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("remote_notice_dialog"))
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
