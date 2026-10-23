package com.dantech.skimbrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.HitTarget
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.ui.navigation.LinkOptionsKey
import com.dantech.skimbrowser.ui.navigation.ReaderKey
import com.dantech.skimbrowser.ui.shell.SkimAppRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An empty WebView pool is what process death leaves behind. Recreating the Activity brings the
 * reader back on the same tab. An incognito sheet is dropped once that session is gone.
 *
 * This runs on a device. The unit-test stand-in is [RestoreAcrossContainersTest].
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {

    @get:Rule
    val skim = SkimAppRule()

    @Test
    fun anEmptyPoolStillShowsTheReaderAndDropsAPrivateSheet() {
        skim.setOnboarded(true)
        val article = skim.pages.url("/article.html")
        val readerTab = skim.openTab(article)
        val scenario = skim.launch()
        val navigator = skim.navigatorOf(scenario)
        val privateTab = if (skim.container.incognitoSession.isSupported) {
            skim.openTab(skim.pages.url("/plain.html"), incognito = true)
        } else {
            null
        }
        val image = HitTarget.Image(src = skim.pages.url("/image.png"), alt = "A red square", linkUrl = article)
        onMain {
            navigator.openReader(readerTab)
            if (privateTab != null) navigator.openLinkOptions(privateTab, image)
        }
        if (privateTab != null) {
            assertTrue(skim.keysOf(scenario).contains(LinkOptionsKey(privateTab, image)))
        }
        assertTrue(runBlocking { skim.tabs.awaitPageReady(readerTab) })

        onMain { skim.activityOf(scenario).browserHostForTests.pool.destroyAll() }
        if (privateTab != null) {
            onMain { skim.container.incognitoSession.end(engine = null) }
        }

        scenario.recreate()

        val keys = skim.keysOf(scenario)
        assertTrue(keys.contains(ReaderKey(readerTab)))
        assertFalse(keys.any { it is LinkOptionsKey && it.tabId == privateTab })
        assertEquals(readerTab, skim.tabs.state.value.currentTabId)
        assertTrue(skim.tabs.state.value.tabs.none { it.incognito })
        assertTrue(runBlocking { skim.tabs.awaitPageReady(readerTab) })
    }
}
