package com.dantech.skimbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopUserAgentTest {

    @Test
    fun aFullWebViewAgentBecomesChromeOnLinux() {
        val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UP1A.231005.007; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/119.0.6045.163 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.163 Safari/537.36",
            desktopUserAgent(webView),
        )
    }

    @Test
    fun aReducedWebViewAgentBecomesChromeOnLinux() {
        val webView = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            desktopUserAgent(webView),
        )
    }

    @Test
    fun anOlderPlatformFormIsReplacedToo() {
        val webView = "Mozilla/5.0 (Linux; U; Android 8.0.0; en-us; SM-G950F Build/R16NW; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/90.0.4430.210 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.210 Safari/537.36",
            desktopUserAgent(webView),
        )
    }

    @Test
    fun theDesktopAgentStaysAsItIs() {
        val desktop = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        assertEquals(desktop, desktopUserAgent(desktop))
        assertEquals(desktop, desktopUserAgent(desktopUserAgent(desktop)))
    }

    @Test
    fun mobileTokensGoEvenWhenThePlatformPartIsUnknown() {
        assertEquals(
            "Mozilla/5.0 (Fuchsia) AppleWebKit/537.36 Chrome/141.0.0.0 Safari/537.36",
            desktopUserAgent("Mozilla/5.0 (Fuchsia; wv) AppleWebKit/537.36 Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36"),
        )
    }
}
