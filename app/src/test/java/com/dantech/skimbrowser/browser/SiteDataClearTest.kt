package com.dantech.skimbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** Cookie names and the host expiry written for the site information sheet. */
@RunWith(AndroidJUnit4::class)
class SiteDataClearTest {

    @Test
    fun cookieNamesKeepOrderAndDropBlanksAndDuplicates() {
        assertEquals(listOf("a", "b", "c"), cookieNames("a=1; b=2; c=3"))
        assertEquals(listOf("a", "b"), cookieNames(" a =1; ; b=2; a=3 "))
        assertEquals(emptyList<String>(), cookieNames(null))
        assertEquals(emptyList<String>(), cookieNames("   "))
    }

    @Test
    fun expireHostCookiesClearsEachNameForThePathAndTheDomainThenFlushesOnce() {
        val stored = mutableMapOf(
            "https://daylightjournal.com" to "session=1; theme=dark",
            "http://daylightjournal.com" to "session=1",
        )
        val writes = mutableListOf<Pair<String, String>>()
        var flushes = 0
        expireHostCookies(
            host = "daylightjournal.com",
            read = { stored[it] },
            write = { url, value -> writes += url to value },
            flush = { flushes++ },
        )

        assertEquals(
            listOf(
                "https://daylightjournal.com" to "session=; Max-Age=0; path=/",
                "https://daylightjournal.com" to "session=; Max-Age=0; path=/; domain=.daylightjournal.com",
                "https://daylightjournal.com" to "theme=; Max-Age=0; path=/",
                "https://daylightjournal.com" to "theme=; Max-Age=0; path=/; domain=.daylightjournal.com",
                "http://daylightjournal.com" to "session=; Max-Age=0; path=/",
                "http://daylightjournal.com" to "session=; Max-Age=0; path=/; domain=.daylightjournal.com",
            ),
            writes,
        )
        assertEquals(1, flushes)
    }

    @Test
    fun expireHostCookiesIgnoresAHostThatIsNotASiteKey() {
        var writes = 0
        var flushes = 0
        expireHostCookies("not a host", { "a=1" }, { _, _ -> writes++ }, { flushes++ })
        expireHostCookies("", { "a=1" }, { _, _ -> writes++ }, { flushes++ })
        expireHostCookies("evil.example/path", { "a=1" }, { _, _ -> writes++ }, { flushes++ })
        assertEquals(0, writes)
        assertEquals(0, flushes)
        assertTrue(writes == 0)
    }
}
