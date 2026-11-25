package com.example

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherIconTest {
    @Test
    fun `adaptive launcher icons and layers resolve`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("com.mythic.script", context.packageName)
        val launcher = context.getDrawable(R.mipmap.ic_launcher)
        val roundLauncher = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue(launcher is AdaptiveIconDrawable)
        assertTrue(roundLauncher is AdaptiveIconDrawable)
        assertNotNull((launcher as AdaptiveIconDrawable).background)
        assertNotNull(launcher.foreground)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_background))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_foreground))
    }
}
