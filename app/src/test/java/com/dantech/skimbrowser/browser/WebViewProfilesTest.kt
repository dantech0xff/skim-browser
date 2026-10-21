package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The JVM has no WebView provider, so this is a WebView without profiles; with them, see the device tests. */
@RunWith(AndroidJUnit4::class)
class WebViewProfilesTest {

    private val profiles = WebViewProfiles()

    @Test
    fun withoutProfilesThereIsNoIncognitoAndNoCacheManager() {
        assertFalse(profiles.isMultiProfileSupported)
        assertFalse(profiles.hasHttpCacheManager)
    }

    @Test
    fun everyProfileCallFailsInsteadOfReachingTheDefaultProfile() {
        assertThrows(UnsupportedOperationException::class.java) { profiles.getOrCreate("incognito-1") }
        assertThrows(UnsupportedOperationException::class.java) { profiles.get("incognito-1") }
        assertThrows(UnsupportedOperationException::class.java) { profiles.names() }
        assertThrows(UnsupportedOperationException::class.java) { profiles.delete("incognito-1") }
    }

    @Test
    fun anIncognitoSessionOverThemIsUnsupported() {
        val session = IncognitoSession(profiles, File("unused"), CoroutineScope(Dispatchers.Unconfined))

        assertFalse(session.isSupported)
        assertThrows(IllegalStateException::class.java) { session.profileForNewTab { } }
    }
}
