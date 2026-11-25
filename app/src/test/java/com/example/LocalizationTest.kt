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
}
