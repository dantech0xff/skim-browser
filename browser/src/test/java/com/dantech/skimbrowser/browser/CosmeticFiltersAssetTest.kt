package com.dantech.skimbrowser.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The compiled EasyList and EasyPrivacy cosmetic rules, and what one page's stylesheet costs. */
@RunWith(AndroidJUnit4::class)
class CosmeticFiltersAssetTest {

    private val filters = ApplicationProvider.getApplicationContext<Context>()
        .assets.open(RequestShield.COSMETIC_ASSET).bufferedReader().use { CosmeticFilters.load(it) }

    @Test
    fun aShortGenericSlotIsHiddenAndAListedExceptionRemovesIt() {
        val page = filters.cssFor("example.com")
        val excepted = filters.cssFor("apkmirror.com")

        assertTrue(page.contains("#ad-1{display:none !important}"))
        assertTrue(page.contains(".adslot{display:none !important}"))
        assertFalse(excepted.contains(".adslot{display:none !important}"))
        assertTrue(filters.cssFor("finance.yahoo.com").contains(".sdaContainer{display:none !important}"))
        assertTrue(page.lines().count { it.isNotEmpty() } <= CosmeticFilters.MAX_SELECTORS)
        assertFalse(page.contains("querySelector"))
    }

    @Test
    fun parsingTheSnapshotAndBuildingOnePageStayCheap() {
        val started = System.nanoTime()
        val again = ApplicationProvider.getApplicationContext<Context>()
            .assets.open(RequestShield.COSMETIC_ASSET).bufferedReader().use { CosmeticFilters.load(it) }
        val parseMs = (System.nanoTime() - started) / 1_000_000
        val lookupStarted = System.nanoTime()
        var rules = 0
        repeat(20) {
            rules += again.cssFor("finance.yahoo.com").lines().count { line -> line.isNotEmpty() }
        }
        val lookupMs = (System.nanoTime() - lookupStarted) / 1_000_000

        assertTrue(rules > 0)
        assertTrue("parse took $parseMs ms", parseMs < 2_000)
        assertTrue("20 stylesheets took $lookupMs ms", lookupMs < 1_000)
        println("cosmetic asset parse $parseMs ms, 20 stylesheets $lookupMs ms, rules ${rules / 20}")
    }
}
