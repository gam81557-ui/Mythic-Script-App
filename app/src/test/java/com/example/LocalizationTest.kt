package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.localization.LocaleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalizationTest {

    @Test
    fun `language selection persists and loads Burmese resources`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            LocaleManager.setLanguage(app, LocaleManager.BURMESE)
            assertEquals(LocaleManager.BURMESE, LocaleManager.currentLanguage(app))

            val burmese = LocaleManager.localizedContext(app)
            assertEquals("စကင် စကရစ်ပ်များ", burmese.getString(R.string.tab_skin_scripts))
            assertNotEquals(
                app.getString(R.string.tab_skin_scripts),
                burmese.getString(R.string.tab_skin_scripts),
            )
        } finally {
            LocaleManager.setLanguage(app, LocaleManager.ENGLISH)
        }

        assertEquals(
            "Skin Scripts",
            LocaleManager.localizedContext(app).getString(R.string.tab_skin_scripts),
        )
    }

    @Test
    fun `Shizuku guide labels and credit are localized`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            LocaleManager.setLanguage(app, LocaleManager.BURMESE)
            val burmese = LocaleManager.localizedContext(app)
            assertEquals("Shizuku စတင်ရန်", burmese.getString(R.string.shizuku_guide_title))
            assertEquals(
                "သင်ခန်းစာ ကြည့်ရန်",
                burmese.getString(R.string.action_watch_tutorial),
            )
            assertEquals("Credit: Kazeno", burmese.getString(R.string.shizuku_tutorial_credit))
            assertNotEquals(
                app.getString(R.string.shizuku_guide_title),
                burmese.getString(R.string.shizuku_guide_title),
            )
        } finally {
            LocaleManager.setLanguage(app, LocaleManager.ENGLISH)
        }

        assertEquals("Start Shizuku", app.getString(R.string.shizuku_guide_title))
        assertEquals("Watch Tutorial", app.getString(R.string.action_watch_tutorial))
        assertEquals("Credit: Kazeno", app.getString(R.string.shizuku_tutorial_credit))
    }

    @Test
    fun `toggling language repeatedly keeps resources in sync`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            LocaleManager.setLanguage(app, LocaleManager.ENGLISH)
            assertEquals("Skin Scripts", app.getString(R.string.tab_skin_scripts))

            assertEquals(LocaleManager.BURMESE, LocaleManager.toggleLanguage(app))
            assertEquals(
                "စကင် စကရစ်ပ်များ",
                LocaleManager.localizedContext(app).getString(R.string.tab_skin_scripts),
            )

            assertEquals(LocaleManager.ENGLISH, LocaleManager.toggleLanguage(app))
            assertEquals(
                "Skin Scripts",
                LocaleManager.localizedContext(app).getString(R.string.tab_skin_scripts),
            )

            assertEquals(LocaleManager.BURMESE, LocaleManager.toggleLanguage(app))
            assertEquals(
                "စကင် စကရစ်ပ်များ",
                LocaleManager.localizedContext(app).getString(R.string.tab_skin_scripts),
            )
        } finally {
            LocaleManager.setLanguage(app, LocaleManager.ENGLISH)
        }
    }
}
