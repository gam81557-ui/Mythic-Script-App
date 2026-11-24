package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RemoteConfig
import com.example.data.remote.RemoteConfigManager
import com.example.domain.model.ScriptLifecycleState
import com.example.domain.model.SkinScriptItem
import com.example.ui.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdGateTest {

    private fun script() = SkinScriptItem(
        scriptId = "Hayabusa-TEST",
        heroId = "hayabusa",
        skinName = "Test Skin",
        tierTag = "",
        version = "1.0",
        compatibilityNotes = "",
        targetRelativePaths = emptyList(),
        stagedDirPath = "",
        isUserImported = false,
        hasBackup = false,
        backupFileCount = 0,
        backupByteSize = 0L,
        lastUpdatedAt = 0L,
        lifecycleState = ScriptLifecycleState.Idle,
        downloadUrl = "https://example.com/skin.zip",
    )

    private fun requireAdConfig(): RemoteConfig = RemoteConfigManager().parseConfigJson(
        """
        {
          "requireAdForDownload": true,
          "Hayabusa-TEST": {
            "hero": "Hayabusa",
            "skin_name": "Test Skin",
            "download": "https://example.com/skin.zip"
          }
        }
        """.trimIndent(),
        fetchedAtMillis = 1L,
    ).getOrThrow()

    @Test
    fun `download and inject both wait for the ad gate`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CompanionViewModel(app)
        viewModel.applyRemoteConfigForTest(requireAdConfig())

        var gateCalls = 0
        // Returning false means "rejected", so no download coroutine starts.
        viewModel.adGate = { _ ->
            gateCalls++
            false
        }

        viewModel.downloadSkinScript(script())
        assertEquals(1, gateCalls)

        // Inject on a skin that is not downloaded yet must use the same gate.
        viewModel.injectScript(script())
        assertEquals(2, gateCalls)
    }
}
