package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Incognito on a WebView without profiles: there is none, and nothing falls back to the profile
 * normal tabs share. Runs only on such a WebView; IncognitoProfileTest covers the others.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoUnavailableTest {

    @get:Rule
    val rig = BrowserRig()

    @Before
    fun profilesAreMissing() {
        WebViewSupport.assumeNoProfiles()
    }

    @Test
    fun withoutProfilesNoIncognitoTabOpensAndNormalTabsStillWork() {
        assertFalse(rig.incognito.isSupported)

        assertThrows(IllegalStateException::class.java) {
            onMain { rig.tabs.newTab(incognito = true, url = rig.pages.url("/plain.html")) }
        }

        assertTrue(rig.tabs.state.value.tabs.none { it.incognito })
        assertEquals(IncognitoSession.State.None, rig.incognito.state.value)
        val normal = rig.newTab(rig.pages.url("/plain.html"))
        assertTrue(runBlocking { rig.tabs.awaitPageReady(normal) })
    }
}
