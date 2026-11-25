package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.catalog.HeroCatalogProvider
import com.example.data.files.ScriptArchiveManager
import com.example.data.local.AppPreferences
import com.example.domain.model.HeroRole
import com.example.ui.CompanionViewModel
import com.example.ui.MythicScriptApp
import com.example.ui.theme.MythicScriptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class CompanionPipelineTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `hero search supports aliases and role filtering`() {
        val assassins = HeroCatalogProvider.heroes.filter {
            it.matchesRole(HeroRole.ASSASSIN) && it.matchesQuery("gossen")
        }
        assertEquals(1, assassins.size)
        assertEquals("gusion", assassins.first().id)

        val yz = HeroCatalogProvider.heroes.filter {
            it.matchesRole(HeroRole.FIGHTER) && it.matchesQuery("YZ")
        }
        assertEquals(1, yz.size)
        assertEquals("yuzhong", yz.first().id)
    }

    @Test
    fun `archive manager blocks zip slip traversal and stages valid bundle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ScriptArchiveManager(context)
        val stagingDir = File(context.cacheDir, "test_stage_${System.nanoTime()}").apply { mkdirs() }

        // Test Zip-Slip rejection
        assertNull(ScriptArchiveManager.sanitizeRelativePath("../../etc/passwd"))
        assertNull(ScriptArchiveManager.sanitizeRelativePath("files/../../../evil.so"))

        // Build valid zip in memory
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write("""{"heroId":"gusion","skinName":"Soul Revelation"}""".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("files/dragon2017/assets/Art/Android/test_skin.unity3d"))
                zos.write("UNITY_DUMMY_PAYLOAD".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        var manifestCaptured = ""
        val extracted = mutableListOf<String>()
        manager.extractZipStreamSafely(
            inputStream = ByteArrayInputStream(zipBytes),
            stagingDir = stagingDir,
            onManifestFound = { manifestCaptured = it },
            onFileExtracted = { relPath, _ -> extracted.add(relPath) },
        )

        assertTrue(manifestCaptured.contains("Soul Revelation"))
        assertEquals(listOf("files/dragon2017/assets/Art/Android/test_skin.unity3d"), extracted)
        assertTrue(File(stagingDir, "files/dragon2017/assets/Art/Android/test_skin.unity3d").exists())
        stagingDir.deleteRecursively()
    }

    @Test
    fun `archive manager rejects oversized manifests and unsafe file segments`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ScriptArchiveManager(context)
        val stagingDir = File(context.cacheDir, "test_stage_limit_${System.nanoTime()}").apply { mkdirs() }

        assertEquals("script", ScriptArchiveManager.sanitizeFileSegment(".."))
        assertEquals("script", ScriptArchiveManager.sanitizeFileSegment("."))

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(ByteArray(ScriptArchiveManager.MAX_MANIFEST_BYTES + 1))
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        var rejected = false
        try {
            manager.extractZipStreamSafely(
                inputStream = ByteArrayInputStream(zipBytes),
                stagingDir = stagingDir,
                onManifestFound = {},
                onFileExtracted = { _, _ -> },
            )
        } catch (error: IOException) {
            rejected = true
            assertTrue(error.message.orEmpty().contains("size limit"))
        } finally {
            stagingDir.deleteRecursively()
        }
        assertTrue("Oversized manifest should be rejected", rejected)
    }

    @Test
    fun `package name validator accepts valid ids and rejects malformed paths`() {
        assertTrue(AppPreferences.isValidPackageName("com.mobile.legends"))
        assertTrue(AppPreferences.isValidPackageName("com.vng.mlbbvn"))
        assertFalse(AppPreferences.isValidPackageName("../evil"))
        assertFalse(AppPreferences.isValidPackageName("invalid"))
    }

    @Test
    fun `in-app download resolver normalizes host links and lifecycle states map cleanly`() {
        assertEquals(
            "https://drive.google.com/uc?export=download&id=ABC123",
            ScriptArchiveManager.normalizeDirectDownloadUrl("https://drive.google.com/file/d/ABC123/view?usp=sharing"),
        )
        assertEquals(
            "https://www.dropbox.com/s/xyz/skin.zip?dl=1",
            ScriptArchiveManager.normalizeDirectDownloadUrl("https://www.dropbox.com/s/xyz/skin.zip?dl=0"),
        )
        assertEquals(
            "https://download2265.mediafire.com/abc/skin.zip",
            ScriptArchiveManager.extractDirectDownloadUrlFromHtml(
                html = """<a id="downloadButton" href="https://download2265.mediafire.com/abc/skin.zip">Download</a>""",
                baseUrl = "https://www.mediafire.com/file/abc/skin.zip/file",
            ),
        )
        assertEquals("Downloading", com.example.domain.model.ScriptLifecycleState.Downloading(0.5f).displayLabel())
        assertEquals("Downloaded", com.example.domain.model.ScriptLifecycleState.Downloaded.displayLabel())
        assertEquals("Failed", com.example.domain.model.ScriptLifecycleState.Error("HTTP 500").displayLabel())
        assertEquals("Unavailable (404)", com.example.domain.model.ScriptLifecycleState.Unavailable(404).displayLabel())
    }

    @Test
    fun `compose ui navigates hero grid filter, stats tab, and remote hero skins with maintenance dialog`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = CompanionViewModel(app)
        val parsed = com.example.data.remote.RemoteConfigManager().parseConfigJson(
            """
            {
              "maintenance": true,
              "maintenance_message": "Maintenance check active",
              "enabled": false,
              "Dyrroth-Orochi": {
                "key": "Dyrroth-Orochi",
                "hero": "Dyrroth",
                "skin_name": "Orochi Chris",
                "download": "https://example.com/dyrroth.zip",
                "img": "https://example.com/dyrroth.png",
                "img2": "",
                "sort": 1,
                "date_upload": "2025-11-10",
                "tag": "https://static.wikia.nocookie.net/mobile-legends/images/4/45/KOF_Tag.png"
              }
            }
            """.trimIndent(),
            fetchedAtMillis = 777L,
        ).getOrThrow()

        composeRule.setContent {
            MythicScriptTheme(dynamicColor = false) {
                MythicScriptApp(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        vm.applyRemoteConfigForTest(parsed)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("notice_dialog_title")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notice_dialog_message").assertIsDisplayed()
        composeRule.onNodeWithTag("notice_got_it_button").performClick()
        composeRule.waitForIdle()

        // Filter Show All & Fighter to verify HeroRoleMapper discovers Dyrroth
        composeRule.onNodeWithTag("hero_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("role_chip_all").performClick()
        composeRule.onNodeWithTag("role_chip_fighter").performClick()
        composeRule.onNodeWithTag("hero_search_input").performTextInput("Dyrroth")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_card_dyrroth").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("script_card_Dyrroth-Orochi"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("script_card_Dyrroth-Orochi").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Orochi Chris").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("download_script_Dyrroth-Orochi").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Dyrroth-Orochi", substring = true))
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("static.wikia", substring = true))
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Uploaded", substring = true))
                .fetchSemanticsNodes().size,
        )

        // Also check built-in hero stats & combos tab via Show All -> Gusion
        composeRule.onNodeWithTag("back_to_heroes_button").performClick()
        composeRule.onNodeWithTag("clear_search_button").performClick()
        composeRule.onNodeWithTag("role_chip_all").performClick()
        composeRule.onNodeWithTag("hero_search_input").performTextInput("Gossen")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_card_gusion").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_tab_stats_combos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Recommended Skill Combos").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `unavailable 404 skins sort to bottom, disable injection, and hide cleanly with toggle`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = CompanionViewModel(app)
        val parsed = com.example.data.remote.RemoteConfigManager().parseConfigJson(
            """
            {
              "maintenance": false,
              "enabled": true,
              "Hayabusa-Dead": {
                "key": "Hayabusa-Dead",
                "hero": "Hayabusa",
                "skin_name": "Broken 404 Skin",
                "download": "https://example.com/missing.zip",
                "img": "",
                "img2": "",
                "sort": 1,
                "date_upload": "2024-01-01",
                "tag": ""
              },
              "Hayabusa-Working": {
                "key": "Hayabusa-Working",
                "hero": "Hayabusa",
                "skin_name": "Shadow of Obscurity",
                "download": "https://example.com/working.zip",
                "img": "",
                "img2": "",
                "sort": 2,
                "date_upload": "2025-10-01",
                "tag": ""
              }
            }
            """.trimIndent(),
            fetchedAtMillis = 888L,
        ).getOrThrow()

        composeRule.setContent {
            MythicScriptTheme(dynamicColor = false) {
                MythicScriptApp(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        vm.applyRemoteConfigForTest(parsed)
        composeRule.waitForIdle()

        // Open Hayabusa from roster
        composeRule.onNodeWithTag("hero_search_input").performTextInput("Hayabusa")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_card_hayabusa").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("script_card_Hayabusa-Dead"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val deadScript = vm.uiState.value.scriptsForSelectedHero.first { it.scriptId == "Hayabusa-Dead" }
        kotlinx.coroutines.runBlocking {
            vm.markScriptUnavailableNowForTest(deadScript, httpCode = 404)
        }

        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value.unavailableCountForHero == 0 && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            Thread.sleep(25)
        }
        val stateAfterMark = vm.uiState.value
        assertEquals(
            "Expected 1 unavailable skin; hero=${stateAfterMark.selectedHeroId}, scripts=${stateAfterMark.scriptsForSelectedHero.map { "${it.scriptId}:${it.lifecycleState.displayLabel()}" }}",
            1,
            stateAfterMark.unavailableCountForHero,
        )
        assertEquals(
            "Expected Hayabusa-Dead last; scripts=${stateAfterMark.scriptsForSelectedHero.map { "${it.scriptId}:${it.lifecycleState.displayLabel()}" }}",
            "Hayabusa-Dead",
            stateAfterMark.scriptsForSelectedHero.lastOrNull()?.scriptId,
        )

        // Verify working skin moved above dead skin and auto-selection avoids the 404 skin
        val orderedIds = vm.uiState.value.scriptsForSelectedHero.map { it.scriptId }
        assertTrue(orderedIds.indexOf("Hayabusa-Working") < orderedIds.indexOf("Hayabusa-Dead"))
        assertTrue(vm.uiState.value.selectedScript?.isUnavailable == false)
        assertTrue(vm.uiState.value.selectedScriptId != "Hayabusa-Dead")
        composeRule.onNodeWithTag("inject_selected_button").assertIsEnabled()

        // Verify unavailable status chip and concise message appear on the 404 card
        composeRule.onNodeWithTag("hero_skins_list").performScrollToNode(hasTestTag("script_card_Hayabusa-Dead"))
        composeRule.onNodeWithTag("script_card_Hayabusa-Dead").assertIsDisplayed()
        composeRule.onNodeWithTag("script_status_Hayabusa-Dead", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("unavailable_notice_Hayabusa-Dead", useUnmergedTree = true).assertExists()
        assertEquals(
            0,
            composeRule.onAllNodes(hasTestTag("download_script_Hayabusa-Dead")).fetchSemanticsNodes().size,
        )

        // Selecting the 404 card disables bottom Inject Selected button cleanly
        composeRule.onNodeWithTag("script_card_Hayabusa-Dead").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inject_selected_button").assertIsNotEnabled()

        // Working skin still shows its Download action and works normally
        composeRule.onNodeWithTag("hero_skins_list").performScrollToNode(hasTestTag("script_card_Hayabusa-Working"))
        composeRule.onNodeWithTag("download_script_Hayabusa-Working").assertIsDisplayed()

        // Toggle Hide Unavailable hides the 404 card cleanly while keeping working skin visible
        composeRule.onNodeWithTag("hero_skins_list").performScrollToNode(hasTestTag("toggle_hide_unavailable_chip"))
        composeRule.onNodeWithTag("toggle_hide_unavailable_chip").performClick()
        composeRule.waitForIdle()
        assertEquals(
            0,
            composeRule.onAllNodes(hasTestTag("script_card_Hayabusa-Dead")).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("hero_skins_list").performScrollToNode(hasTestTag("script_card_Hayabusa-Working"))
        composeRule.onNodeWithTag("script_card_Hayabusa-Working").assertIsDisplayed()
    }

    @Test
    fun `hero card image in hero list always displays hero portrait instead of skin image`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = CompanionViewModel(app)
        val parsed = com.example.data.remote.RemoteConfigManager().parseConfigJson(
            """
            {
              "maintenance": false,
              "enabled": true,
              "Gusion-KOF": {
                "key": "Gusion-KOF",
                "hero": "Gusion",
                "skin_name": "K'",
                "download": "https://example.com/gusion_kof.zip",
                "img": "https://example.com/gusion_kof_skin_art.png",
                "img2": "",
                "sort": 15,
                "date_upload": "2025-10-01",
                "tag": "KOF"
              },
              "Gusion-Base": {
                "key": "Gusion-Base",
                "hero": "Gusion",
                "skin_name": "",
                "download": "https://example.com/gusion_default.zip",
                "img": "https://static.wikia.nocookie.net/mobile-legends/images/a/a2/Hero561-portrait.png/revision/latest",
                "img2": "",
                "sort": 0,
                "date_upload": "2025-09-01",
                "tag": ""
              }
            }
            """.trimIndent(),
            fetchedAtMillis = 999L,
        ).getOrThrow()

        vm.applyRemoteConfigForTest(parsed)

        val gusion = vm.uiState.value.allHeroes.first { it.id == "gusion" }
        // Verify hero portrait is the official Gusion portrait (Hero561), NOT the skin art (gusion_kof_skin_art.png)
        assertTrue(
            "Expected hero portrait (Hero561) but got ${gusion.previewImageUrl}",
            gusion.previewImageUrl.contains("Hero561-portrait"),
        )
        assertFalse(
            "Hero preview image must never be a skin image",
            gusion.previewImageUrl.contains("gusion_kof_skin_art.png"),
        )

        // Verify HeroPortraitProvider has canonical portraits for heroes
        val hayabusaPortrait = com.example.data.catalog.HeroPortraitProvider.getPortraitUrl("Hayabusa")
        assertTrue(
            "Expected Hayabusa official portrait Hero211 but got $hayabusaPortrait",
            hayabusaPortrait.contains("Hero211-portrait"),
        )
    }
}
