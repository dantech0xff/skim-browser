package com.dantech.skimbrowser.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled cookie-banner hides, parsed the same way as the ad cosmetic rules. */
@RunWith(AndroidJUnit4::class)
class CookieFiltersAssetTest {

    @Test
    fun aCommonBannerIsHiddenAndParsingStaysOffTheHotPath() {
        val started = System.nanoTime()
        val filters = ApplicationProvider.getApplicationContext<Context>()
            .assets.open(RequestShield.COOKIE_ASSET).bufferedReader().use { CosmeticFilters.load(it) }
        val parseMs = (System.nanoTime() - started) / 1_000_000
        val page = filters.cssFor("example.com")

        assertTrue(page.contains("#onetrust-consent-sdk{display:none !important}"))
        assertTrue(page.contains("#CybotCookiebotDialog{display:none !important}"))
        assertTrue(page.lines().count { it.isNotEmpty() } <= CosmeticFilters.MAX_SELECTORS)
        assertTrue("parse took $parseMs ms", parseMs < 2_000)
    }
}
