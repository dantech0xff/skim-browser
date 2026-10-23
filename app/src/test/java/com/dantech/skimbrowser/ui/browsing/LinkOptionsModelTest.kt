package com.dantech.skimbrowser.ui.browsing

import com.dantech.skimbrowser.domain.tab.HitTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** The rows and header of Link options and Image options, without a WebView. */
@RunWith(AndroidJUnit4::class)
class LinkOptionsModelTest {

    private val link = HitTarget.Link("https://www.energysaver.example/rebates-2026", "2026 rebate guide")
    private val image = HitTarget.Image("https://cdn.example/pump.jpg", "A heat pump", "https://daylightjournal.com/a")

    @Test
    fun aNormalTabOffersIncognitoAndAnIncognitoTabDoesNot() {
        assertEquals(
            listOf(LinkRow.OpenNewTab, LinkRow.OpenIncognito, LinkRow.CopyLink, LinkRow.ShareLink),
            linkRows(link, incognito = false),
        )
        assertEquals(
            listOf(LinkRow.OpenNewTab, LinkRow.CopyLink, LinkRow.ShareLink),
            linkRows(link, incognito = true),
        )
        assertFalse(opensIncognito(tabIncognito = false, LinkRow.OpenNewTab))
        assertTrue(opensIncognito(tabIncognito = true, LinkRow.OpenNewTab))
        assertTrue(opensIncognito(tabIncognito = false, LinkRow.OpenIncognito))
    }

    @Test
    fun anImageOffersOpenDownloadAndShareAndFollowsTheTab() {
        assertEquals(
            listOf(LinkRow.OpenImage, LinkRow.DownloadImage, LinkRow.ShareImage),
            linkRows(image, incognito = false),
        )
        assertEquals("https://cdn.example/pump.jpg", linkTargetUrl(image))
        assertTrue(opensIncognito(tabIncognito = true, LinkRow.OpenImage))
        assertFalse(opensIncognito(tabIncognito = false, LinkRow.OpenImage))
    }

    @Test
    fun theHeaderUsesTheLinkTextOrImageAltOverTheAddressWithoutItsScheme() {
        assertEquals("2026 rebate guide" to "www.energysaver.example/rebates-2026", linkHeader(link))
        assertEquals("A heat pump" to "cdn.example/pump.jpg", linkHeader(image))
        assertEquals(
            "example.com" to "example.com/path",
            linkHeader(HitTarget.Link("https://example.com/path", "  ")),
        )
    }
}
