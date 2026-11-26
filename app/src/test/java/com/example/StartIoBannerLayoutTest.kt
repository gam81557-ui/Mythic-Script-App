package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MythicScriptTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class StartIoBannerLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `banner container remains visible with a 50dp strip`() {
        composeRule.setContent {
            MythicScriptTheme(darkTheme = true) {
                StartIoBanner()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("startio_banner_container").assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("startio_banner_container").getUnclippedBoundsInRoot()
        assertTrue("banner strip should be at least 50dp tall", (bounds.bottom - bounds.top) >= 50.dp)
        assertTrue("banner strip should have a positive width", (bounds.right - bounds.left) > 0.dp)
    }
}
