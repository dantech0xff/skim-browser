package com.dantech.skimbrowser.browser

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowWebSettings

@RunWith(AndroidJUnit4::class)
class DesktopModeTest {

    private val view = WebView(ApplicationProvider.getApplicationContext<Context>())

    @Before
    fun setUp() {
        ShadowWebSettings.setDefaultUserAgent(MOBILE_UA)
    }

    @After
    fun tearDown() {
        ShadowWebSettings.reset()
    }

    @Test
    fun desktopSiteUsesTheDesktopAgentOfThisWebViewAndAWideViewport() {
        applyDesktopMode(view, on = true)

        assertEquals(desktopUserAgent(MOBILE_UA), view.settings.userAgentString)
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.122 Safari/537.36",
            view.settings.userAgentString,
        )
        assertTrue(view.settings.useWideViewPort)
        assertTrue(view.settings.loadWithOverviewMode)
    }

    @Test
    fun turningItOffGoesBackToTheWebViewsDefaults() {
        applyDesktopMode(view, on = true)

        applyDesktopMode(view, on = false)

        assertEquals("the WebView's own agent again", MOBILE_UA, view.settings.userAgentString)
        assertFalse(view.settings.useWideViewPort)
        assertFalse(view.settings.loadWithOverviewMode)
    }

    private companion object {
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 16; Pixel 9 Build/BP2A.250605.031; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
    }
}
