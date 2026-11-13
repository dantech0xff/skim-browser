package com.dantech.skimbrowser.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticFiltersTest {

    @Test
    fun aGenericSelectorIsHiddenOnAnySiteAndAnExceptionRemovesIt() {
        val filters = load(
            "G\t.adsbox",
            "G\t.skip",
            "E\t.skip",
            "G\tbody",
            "G\tdiv:-abp-has(.ad)",
        )

        val css = filters.cssFor("news.example")

        assertTrue(css.contains(".adsbox{display:none !important}"))
        assertFalse(css.contains(".skip"))
        assertFalse(css.contains("body{"))
        assertFalse(css.contains(":-abp-"))
    }

    @Test
    fun aDomainSelectorAppliesToThatHostAndItsSubdomainsOnly() {
        val filters = load(
            "H\texample.com\t.banner",
            "H\tother.example\t.other",
            "X\texample.com\t.adsbox",
            "G\t.adsbox",
        )

        val page = filters.cssFor("www.example.com")
        val child = filters.cssFor("a.example.com")
        val elsewhere = filters.cssFor("other.example")

        assertTrue(page.contains(".banner{display:none !important}"))
        assertFalse(page.contains(".adsbox"))
        assertTrue(child.contains(".banner{display:none !important}"))
        assertFalse(child.contains(".adsbox"))
        assertTrue(elsewhere.contains(".other{display:none !important}"))
        assertTrue(elsewhere.contains(".adsbox{display:none !important}"))
        assertFalse(elsewhere.contains(".banner"))
    }

    @Test
    fun aNegatedDomainKeepsTheSelectorOffThatHost() {
        val filters = load(
            "G\t.popup\t!news.example",
            "H\texample.com\t.slot\t!shop.example.com",
        )

        assertFalse(filters.cssFor("news.example").contains(".popup"))
        assertTrue(filters.cssFor("other.example").contains(".popup{display:none !important}"))
        assertTrue(filters.cssFor("example.com").contains(".slot{display:none !important}"))
        assertFalse(filters.cssFor("shop.example.com").contains(".slot"))
        assertTrue(filters.cssFor("news.shop.example.com").let { !it.contains(".slot") })
    }

    @Test
    fun theHeaderAndUnknownKindsAreIgnored() {
        val filters = load(
            "# License: GPL-3.0-or-later",
            "Z\t.nope",
            "G\t.ok",
        )

        assertEquals(".ok{display:none !important}\n", filters.cssFor("example.com"))
    }

    @Test
    fun aPageKeepsGenericRulesWhenTheDomainListIsLongerThanTheCap() {
        val lines = ArrayList<String>()
        repeat(CosmeticFilters.MAX_GENERIC_SELECTORS) { lines += "G\t.g$it" }
        lines += "G\t" + "x".repeat(80)
        repeat(CosmeticFilters.MAX_SELECTORS) { lines += "H\texample.com\t.s$it" }
        val filters = load(*lines.toTypedArray())

        val css = filters.cssFor("example.com")
        val rules = css.lines().count { it.isNotEmpty() }

        assertEquals(CosmeticFilters.MAX_SELECTORS, rules)
        assertTrue(css.contains(".g0{display:none !important}"))
        assertFalse(css.contains("x".repeat(80)))
        assertTrue(css.contains(".s0{display:none !important}"))
    }

    @Test
    fun aBlockedHostCollapsesItsFrameAndNotALookalike() {
        val css = CosmeticFilters.collapseBlockedCss(listOf("ads.example", "bad host", "x", "ads.example"))

        assertTrue(css.contains("iframe[src*=\"//ads.example/\"]"))
        assertTrue(css.contains("img[src*=\"//www.ads.example/\"]"))
        assertTrue(css.contains("embed[src$=\"//ads.example\"]"))
        assertTrue(css.contains("[src*=\"//ads.example#\"]"))
        assertTrue(css.contains("{display:none !important}"))
        assertFalse(css.contains("bad host"))
        assertFalse(css.contains("://x"))
        assertEquals(1, css.lines().count { it.isNotEmpty() })
    }

    @Test
    fun collapseStopsAfterTheHostCap() {
        val hosts = (0 until CosmeticFilters.MAX_COLLAPSE_HOSTS + 10).map { "h$it.example" }
        val css = CosmeticFilters.collapseBlockedCss(hosts)
        assertEquals(CosmeticFilters.MAX_COLLAPSE_HOSTS, css.lines().count { it.isNotEmpty() })
        assertTrue(css.contains("h0.example"))
        assertFalse(css.contains("h${CosmeticFilters.MAX_COLLAPSE_HOSTS}.example"))
    }

    @Test
    fun parsingAndLookupStayCheapOnALargeList() {
        val lines = ArrayList<String>(20_000)
        repeat(20_000) { lines += "H\th$it.example\t.ad$it" }
        val started = System.nanoTime()
        val filters = load(*lines.toTypedArray())
        val parseMs = (System.nanoTime() - started) / 1_000_000
        val lookupStarted = System.nanoTime()
        var hits = 0
        repeat(1_000) { index ->
            if (filters.cssFor("h${index}.example").contains(".ad$index")) hits++
        }
        val lookupMs = (System.nanoTime() - lookupStarted) / 1_000_000

        assertEquals(1_000, hits)
        assertTrue("parse took $parseMs ms", parseMs < 2_000)
        assertTrue("1000 lookups took $lookupMs ms", lookupMs < 1_000)
        println("cosmetic parse 20k in $parseMs ms, 1000 lookups in $lookupMs ms")
    }

    @Test
    fun theHideScriptInstallsOneStyleAndDoesNotWalkThePage() {
        val script = CosmeticScript.hide("www.Example.com", ".adsbox{display:none !important}\n")

        assertTrue(script.contains("display:none !important"))
        assertTrue(script.contains("skim-hide"))
        assertTrue(script.contains("\"example.com\""))
        assertTrue(script.contains("www."))
        assertTrue(script.contains("\\n"))
        assertFalse(script.contains("\n"))
        assertFalse(script.contains("querySelector"))
        assertFalse(script.contains("getElementsBy"))
    }

    @Test
    fun theCookieScriptHidesBannersAndRestoresScrollOnThatHostOnly() {
        val script = CosmeticScript.cookie("www.Example.com", "#cookie-banner{display:none !important}\n")

        assertTrue(script.contains("skim-cookie"))
        assertTrue(script.contains("\"example.com\""))
        assertTrue(script.contains("overflow"))
        assertTrue(script.contains("position"))
        assertTrue(script.contains("#onetrust-consent-sdk"))
        assertTrue(script.contains("\\n"))
        assertFalse(script.contains("\n"))
    }

    @Test
    fun aQuoteInTheStylesheetStaysInsideTheScriptString() {
        val script = CosmeticScript.collapse("a[href=\"x\"]{display:none !important}")

        assertTrue(script.contains("\\\"x\\\""))
        assertFalse(script.contains("querySelector"))
        assertTrue(script.contains("skim-collapse"))
    }

    private fun load(vararg lines: String): CosmeticFilters =
        CosmeticFilters.load(lines.joinToString("\n").reader().buffered())
}
