package com.dantech.skimbrowser.domain.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPartsTest {

    @Test
    fun theHostIsLowercaseWithoutALeadingWww() {
        assertEquals("daylightjournal.com", hostOf("https://WWW.DaylightJournal.COM/energy/heat-pumps-cold"))
        assertEquals("kotlinlang.org", hostOf("http://kotlinlang.org"))
        assertEquals("www2.example.com", hostOf("https://www2.example.com/"))
        assertEquals("sub.www.example.com", hostOf("https://sub.www.example.com/"))
    }

    @Test
    fun userInfoAndPortAreNotPartOfTheHost() {
        assertEquals("example.com", hostOf("https://user:secret@Example.com:8443/a?b=1#c"))
        assertEquals("example.com", hostOf("http://user@www.example.com:80/"))
    }

    @Test
    fun ipHostsAreKeptAndIpv6KeepsItsBrackets() {
        assertEquals("[::1]", hostOf("http://[::1]:8080/"))
        assertEquals("[2001:db8::1]", hostOf("https://[2001:DB8::1]/"))
        assertEquals("192.168.1.10", hostOf("http://192.168.1.10:8080/router"))
    }

    @Test
    fun urlsWithoutAWebHostHaveNoSite() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<p>hi</p>",
            "about:blank",
            "file:///sdcard/Download/page.html",
            "view-source:https://example.com/",
            "blob:https://example.com/0b6c4f30",
            "intent://scan/#Intent;scheme=zxing;end",
            "mailto:someone@example.com",
            "ftp://ftp.example.com/",
            "example.com",
            "https://",
            "http:",
            "https:",
            "https:example.com",
            "http://exa mple.com/",
            "",
            "   ",
        ).forEach { assertNull(it, hostOf(it)) }
    }

    @Test
    fun aBackslashEndsTheHostAsInABrowser() {
        assertEquals("evil.example", hostOf("https://evil.example\\@good.example/"))
    }

    @Test
    fun aUnicodeHostBecomesItsPunycodeForm() {
        assertEquals("xn--bcher-kva.example", hostOf("https://bücher.example/"))
        assertEquals("xn--bcher-kva.example", hostOf("https://xn--bcher-kva.example/"))
    }

    @Test
    fun webUrlsAreHttpOrHttpsInAnyLetterCase() {
        listOf("http://a.example", "HTTPS://a.example", "Http://a.example", "  https://a.example  ").forEach {
            assertTrue(it, isWebUrl(it))
        }
        listOf("ftp://a.example", "httpx://a.example", "http:", "https:", "javascript:http://a.example", "about:blank", "", "file:///a", "view-source:https://a.example").forEach {
            assertFalse(it, isWebUrl(it))
        }
    }

    @Test
    fun withoutFragmentDropsOnlyTheFragment() {
        assertEquals("https://a.example/page?q=1", withoutFragment("https://a.example/page?q=1#section-2"))
        assertEquals("https://a.example/", withoutFragment("https://a.example/#"))
        assertEquals("https://a.example/page", withoutFragment("https://a.example/page"))
        assertEquals("https://a.example/page", withoutFragment("https://a.example/page#a#b"))
    }
}
