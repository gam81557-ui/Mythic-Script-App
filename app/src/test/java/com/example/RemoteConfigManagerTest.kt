package com.example

import com.example.data.remote.RemoteConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteConfigManagerTest {

    private val manager = RemoteConfigManager()

    @Test
    fun `cache busting appends timestamp query param cleanly`() {
        val url = RemoteConfigManager.buildCacheBustedUrl(
            baseUrl = RemoteConfigManager.DEFAULT_CONFIG_URL,
            timestampMillis = 1763880000000L,
        )
        assertEquals(
            "https://raw.githubusercontent.com/gam81557-ui/MLBBSkin-Config/refs/heads/main/unlockskins_fixed.json?t=1763880000000",
            url,
        )

        val urlWithExistingParam = RemoteConfigManager.buildCacheBustedUrl(
            baseUrl = "https://example.com/config.json?env=prod",
            timestampMillis = 12345L,
        )
        assertEquals("https://example.com/config.json?env=prod&t=12345", urlWithExistingParam)
        assertEquals(20_000, RemoteConfigManager.CONNECT_TIMEOUT_MS)
        assertEquals(20_000, RemoteConfigManager.READ_TIMEOUT_MS)
    }

    @Test
    fun `parses full skin schema fields across entire payload sorted alphabetically and by sort order`() {
        val raw = """
            {
              "maintenance": false,
              "maintenance_message": "All systems operational",
              "enabled": true,
              "Zilong-ABC123": {
                "key": "Zilong-ABC123",
                "hero": "  Zilong ",
                "skin_name": "Glorious General",
                "download": "https://cdn.example.com/zilong.zip",
                "img": "https://cdn.example.com/zilong.png",
                "img2": "https://cdn.example.com/zilong_alt.png",
                "sort": 1,
                "date_upload": "2025-11-01",
                "tag": "Epic"
              },
              "Hayabusa-PbWHe69JEY": {
                "key": "Hayabusa-PbWHe69JEY",
                "hero": "Hayabusa",
                "skin_name": "Shura",
                "download": "https://cdn.example.com/shura.zip",
                "img": "https://cdn.example.com/shura.png",
                "img2": "https://cdn.example.com/shura_alt.png",
                "sort": 2,
                "date_upload": "2025-10-12",
                "tag": "11.11"
              },
              "Hayabusa-XYZ999": {
                "key": "Hayabusa-XYZ999",
                "hero": "Hayabusa",
                "skin_name": "Shadow of Obscurity",
                "download": "https://cdn.example.com/shadow.zip",
                "img": "",
                "img2": "https://cdn.example.com/shadow_alt.png",
                "sort": 1,
                "date_upload": "2025-10-15",
                "tag": "Epic"
              },
              "Dyrroth-LMN456": {
                "key": "Dyrroth-LMN456",
                "hero": "Dyrroth",
                "skin_name": "K.O.F. Orochi Chris"
              },
              "EmptyHero-1": {
                "hero": "   ",
                "skin_name": "Ignored"
              },
              "NullHero-2": {
                "hero": null,
                "skin_name": "Ignored"
              },
              "MissingHero-3": {
                "skin_name": "Ignored"
              }
            }
        """.trimIndent()

        val result = manager.parseConfigJson(raw, fetchedAtMillis = 999L)
        assertTrue(result.isSuccess)
        val cfg = result.getOrThrow()

        assertEquals("JSONObject", cfg.rawRootType)
        assertFalse(cfg.maintenanceMode)
        assertTrue(cfg.injectionEnabled)
        assertTrue(cfg.canInjectRemotely)

        // Collected all unique heroes, trimmed, ignoring empty/null, sorted alphabetically
        assertEquals(listOf("Dyrroth", "Hayabusa", "Zilong"), cfg.availableHeroes)
        assertEquals(4, cfg.skinEntries.size)

        // Maps each hero to its skins, ordered by sort ascending
        val hayabusaSkins = cfg.skinsByHero["Hayabusa"].orEmpty()
        assertEquals(2, hayabusaSkins.size)
        assertEquals("Hayabusa-XYZ999", hayabusaSkins[0].id)
        assertEquals("Shadow of Obscurity", hayabusaSkins[0].name)
        assertEquals("https://cdn.example.com/shadow_alt.png", hayabusaSkins[0].imageUrl)
        assertEquals("Hayabusa-PbWHe69JEY", hayabusaSkins[1].id)
        assertEquals("Shura", hayabusaSkins[1].name)
        assertEquals("https://cdn.example.com/shura.zip", hayabusaSkins[1].downloadUrl)
        assertEquals("2025-10-12", hayabusaSkins[1].dateUpload)
        assertEquals("11.11", hayabusaSkins[1].tierTag)
        assertEquals("", hayabusaSkins[1].tagBadgeUrl)
        assertEquals("Zilong", cfg.skinsByHero["Zilong"]?.first()?.heroName)
    }

    @Test
    fun `parses tag URLs into tagBadgeUrl without exposing URL as tierTag and falls back blank skin_name to Default Skin`() {
        val raw = """
            {
              "Hayabusa-NQwGDFaFaV5b-123": {
                "key": "Hayabusa-NQwGDFaFaV5b-123",
                "hero": "Hayabusa",
                "skin_name": "Future Enforcer",
                "img": "https://cdn.example.com/future.png",
                "img2": "",
                "tag": "https://static.wikia.nocookie.net/mobile-legends/images/4/45/Special_Tag.png"
              },
              "Hayabusa-BlankSkin-999": {
                "key": "Hayabusa-BlankSkin-999",
                "hero": "Hayabusa",
                "skin_name": "   ",
                "tag": ""
              }
            }
        """.trimIndent()

        val cfg = manager.parseConfigJson(raw).getOrThrow()
        val skins = cfg.skinsByHero["Hayabusa"].orEmpty()
        assertEquals(2, skins.size)

        val blankSkin = skins.first { it.id == "Hayabusa-BlankSkin-999" }
        assertEquals("Default Skin", blankSkin.name)
        assertEquals("", blankSkin.tierTag)
        assertEquals("", blankSkin.tagBadgeUrl)

        val urlTagSkin = skins.first { it.id == "Hayabusa-NQwGDFaFaV5b-123" }
        assertEquals("Future Enforcer", urlTagSkin.name)
        assertEquals("", urlTagSkin.tierTag)
        assertEquals("https://static.wikia.nocookie.net/mobile-legends/images/4/45/Special_Tag.png", urlTagSkin.tagBadgeUrl)
    }

    @Test
    fun `hero role mapper assigns known MLBB heroes cleanly`() {
        assertEquals(com.example.domain.model.HeroRole.ASSASSIN, com.example.data.catalog.HeroRoleMapper.resolveRole("Hayabusa"))
        assertEquals(com.example.domain.model.HeroRole.FIGHTER, com.example.data.catalog.HeroRoleMapper.resolveRole("Dyrroth"))
        assertEquals(com.example.domain.model.HeroRole.MAGE, com.example.data.catalog.HeroRoleMapper.resolveRole("Kagura"))
        assertEquals(com.example.domain.model.HeroRole.MARKSMAN, com.example.data.catalog.HeroRoleMapper.resolveRole("Layla"))
        assertEquals(com.example.domain.model.HeroRole.TANK, com.example.data.catalog.HeroRoleMapper.resolveRole("Tigreal"))
        assertEquals(com.example.domain.model.HeroRole.SUPPORT, com.example.data.catalog.HeroRoleMapper.resolveRole("Estes"))
        assertEquals("Show All", com.example.domain.model.HeroRole.ALL.label)
    }

    @Test
    fun `parses JSONObject root with maintenance mode enabled`() {
        val raw = """
            {
              "maintenance": true,
              "maintenance_message": "Server patch 1.9.32 verification in progress",
              "enabled": false,
              "Layla-1": {
                "hero": "Layla",
                "skin": "Blue Specter"
              }
            }
        """.trimIndent()

        val result = manager.parseConfigJson(raw, fetchedAtMillis = 123L)
        assertTrue(result.isSuccess)
        val cfg = result.getOrThrow()
        assertTrue(cfg.maintenanceMode)
        assertEquals("Server patch 1.9.32 verification in progress", cfg.maintenanceMessage)
        assertFalse(cfg.injectionEnabled)
        assertFalse(cfg.canInjectRemotely)
        assertEquals(listOf("Layla"), cfg.availableHeroes)
    }

    @Test
    fun `parses JSONArray root with skin entries and control flags`() {
        val raw = """
            [
              {
                "maintenanceMode": false,
                "unlockSkins": true,
                "version": "3.0"
              },
              {
                "scriptId": "sc_gusion_k",
                "hero": "Gusion",
                "skinName": "KOF K'",
                "unlocked": true,
                "notes": "Verified working"
              },
              {
                "scriptId": "sc_ling_collector",
                "hero": "Ling",
                "skinName": "Serene Plume",
                "unlocked": false
              }
            ]
        """.trimIndent()

        val result = manager.parseConfigJson(raw, fetchedAtMillis = 555L)
        assertTrue(result.isSuccess)
        val cfg = result.getOrThrow()
        assertEquals("JSONArray", cfg.rawRootType)
        assertFalse(cfg.maintenanceMode)
        assertTrue(cfg.skinsUnlocked)
        assertTrue(cfg.canInjectRemotely)
        assertEquals(listOf("Gusion", "Ling"), cfg.availableHeroes)
        assertEquals(2, cfg.skinEntries.size)
        assertEquals("sc_gusion_k", cfg.skinEntries[0].id)
        assertTrue(cfg.skinEntries[0].unlocked)
        assertFalse(cfg.skinEntries[1].unlocked)
    }

    @Test
    fun `empty or malformed json returns failure without throwing`() {
        assertTrue(manager.parseConfigJson("").isFailure)
        assertTrue(manager.parseConfigJson("{bad json").isFailure)
        assertTrue(manager.parseConfigJson("\"just a string\"").isFailure)
    }

    @Test
    fun `requireAdForDownload defaults to true when flag is absent`() {
        val raw = """
            {
              "enabled": true,
              "Hayabusa-XYZ999": { "hero": "Hayabusa", "skin_name": "Shadow" }
            }
        """.trimIndent()

        val cfg = manager.parseConfigJson(raw).getOrThrow()
        assertTrue(cfg.requireAdForDownload)
    }

    @Test
    fun `requireAdForDownload reads root and feature flag values`() {
        val rootFlag = manager.parseConfigJson(
            """{ "requireAdForDownload": false, "Hayabusa-XYZ999": { "hero": "Hayabusa", "skin_name": "Shadow" } }"""
        ).getOrThrow()
        assertFalse(rootFlag.requireAdForDownload)

        val nestedFlag = manager.parseConfigJson(
            """{ "featureFlags": { "requireAdForDownload": false }, "Hayabusa-XYZ999": { "hero": "Hayabusa", "skin_name": "Shadow" } }"""
        ).getOrThrow()
        assertFalse(nestedFlag.requireAdForDownload)

        val arrayRoot = manager.parseConfigJson(
            """[ { "requireAdForDownload": false }, { "hero": "Ling", "skin_name": "Serene Plume" } ]"""
        ).getOrThrow()
        assertFalse(arrayRoot.requireAdForDownload)
    }
}
