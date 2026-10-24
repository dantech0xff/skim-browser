package com.dantech.skimbrowser.search

import com.dantech.skimbrowser.data.settings.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Enter in the search field opens for every kind of input. */
class OmniboxResolverTest {

    // Nothing to open

    @Test
    fun blankInputOpensNothing() {
        assertNull(open(""))
        assertNull(open("   "))
        assertNull(open("\t\n "))
        assertNull(omniboxAddress(""))
    }

    // Hosts without a scheme open over https

    @Test
    fun aDomainOpensOverHttps() {
        assertEquals("https://example.com", open("example.com"))
        assertEquals("https://www.example.com", open("www.example.com"))
        assertEquals("https://EXAMPLE.COM", open("EXAMPLE.COM"))
    }

    @Test
    fun aDomainKeepsItsPathQueryAndFragment() {
        assertEquals("https://example.com/path?q=1", open("example.com/path?q=1"))
        assertEquals("https://example.com?q=a", open("example.com?q=a"))
        assertEquals("https://example.com#top", open("example.com#top"))
        assertEquals("https://example.com:8080/path", open("example.com:8080/path"))
    }

    @Test
    fun localhostOpensOverHttpsWithOrWithoutAPort() {
        assertEquals("https://localhost:8080", open("localhost:8080"))
        assertEquals("https://localhost", open("localhost"))
        assertEquals("https://LOCALHOST:3000/app", open("LOCALHOST:3000/app"))
    }

    @Test
    fun anIpv4AddressOpensOverHttps() {
        assertEquals("https://192.168.1.1", open("192.168.1.1"))
        assertEquals("https://10.0.0.1:8443/admin", open("10.0.0.1:8443/admin"))
        assertEquals("https://0.0.0.0", open("0.0.0.0"))
    }

    @Test
    fun aBracketedIpv6AddressOpensOverHttps() {
        assertEquals("https://[::1]", open("[::1]"))
        assertEquals("https://[2001:db8::1]:8080/x", open("[2001:db8::1]:8080/x"))
    }

    @Test
    fun theEndsAreTrimmedFirst() {
        assertEquals("https://example.com", open("  example.com  "))
        assertEquals(search("heat pump"), open("  heat pump \n"))
        assertEquals("https://example.com", omniboxAddress(" example.com "))
    }

    // http and https URLs stay as typed

    @Test
    fun anHttpOrHttpsUrlStaysAsTyped() {
        assertEquals("http://a.b", open("http://a.b"))
        assertEquals("https://example.com/a?b=c#d", open("https://example.com/a?b=c#d"))
        assertEquals("http://intranet/wiki", open("http://intranet/wiki"))
    }

    @Test
    fun theSchemeIsMatchedInAnyLetterCase() {
        assertEquals("HTTP://UPPER.CASE", open("HTTP://UPPER.CASE"))
        assertEquals("HtTpS://example.com", open("HtTpS://example.com"))
    }

    @Test
    fun anHttpUrlThatDoesNotParseIsSearchedFor() {
        listOf("http://", "https://", "https://example.com:99999", "http://exa mple.com", "https://ex%ample.com")
            .forEach { input -> assertEquals(input, search(input), open(input)) }
    }

    // Text is searched for with the engine, encoded

    @Test
    fun wordsAreSearchedForWithTheChosenEngine() {
        assertEquals("https://www.google.com/search?q=heat+pump+vs+furnace", open("heat pump vs furnace"))
        assertEquals("https://www.bing.com/search?q=heat+pump", open("heat pump", SearchEngine.BING))
        assertEquals("https://duckduckgo.com/?q=heat+pump", open("heat pump", SearchEngine.DUCKDUCKGO))
        assertEquals("https://www.ecosia.org/search?q=heat+pump", open("heat pump", SearchEngine.ECOSIA))
    }

    @Test
    fun otherSchemesAreSearchedForAsText() {
        assertEquals("https://www.google.com/search?q=javascript%3Aalert%281%29", open("javascript:alert(1)"))
        assertEquals("https://www.google.com/search?q=file%3A%2F%2F%2Fsdcard", open("file:///sdcard"))
        assertEquals("https://www.google.com/search?q=intent%3A%2F%2Fx%23Intent%3Bend", open("intent://x#Intent;end"))
        assertEquals("https://www.google.com/search?q=about%3Ablank", open("about:blank"))
        listOf(
            "JavaScript:alert(1)",
            "content://media/external/images/1",
            "data:text/html,<script>alert(1)</script>",
            "mailto:someone@example.com",
            "view-source:https://example.com",
            "ftp://example.com/file",
            "blob:https://example.com/0f1e",
            "chrome://settings",
            "localhost:alert(1)",
            "javascript.com:alert(1)",
        ).forEach { input ->
            assertEquals(input, search(input), open(input))
            assertNull(input, omniboxAddress(input))
        }
    }

    // Hosts that are not valid are text

    @Test
    fun aPortOutsideOneTo65535IsText() {
        listOf("example.com:99999", "example.com:65536", "example.com:0", "example.com:", "example.com:80a", "localhost:-1")
            .forEach { input -> assertEquals(input, search(input), open(input)) }
        assertEquals("https://example.com:65535", open("example.com:65535"))
        assertEquals("https://example.com:1", open("example.com:1"))
    }

    @Test
    fun badHostLabelsAreText() {
        listOf(
            "-example.com",
            "example-.com",
            "exa..mple.com",
            ".example.com",
            "example.com.",
            "exa_mple.com",
            "exa\$mple.com",
            "user@example.com",
            "example.c-",
        ).forEach { input -> assertEquals(input, search(input), open(input)) }
    }

    @Test
    fun numbersWithDotsAreText() {
        listOf("1.5", "3.14", "2.0.1", "256.1.1.1", "1.2.3.4.5", "1.2.3.1234")
            .forEach { input -> assertEquals(input, search(input), open(input)) }
    }

    @Test
    fun aSingleWordIsText() {
        assertEquals(search("intranet"), open("intranet"))
        assertEquals(search("router"), open("router"))
    }

    @Test
    fun aBrokenIpv6AddressIsText() {
        listOf("[::1", "[zz::1]", "[::1]x", "::1", "[]", "[::1]:99999")
            .forEach { input -> assertEquals(input, search(input), open(input)) }
    }

    @Test
    fun hostsAreLimitedTo63CharactersALabelAnd253InAll() {
        val longestLabel = "a".repeat(63)
        assertEquals("https://$longestLabel.com", open("$longestLabel.com"))
        val tooLongLabel = "a".repeat(64) + ".com"
        assertEquals(search(tooLongLabel), open(tooLongLabel))

        val label = "a".repeat(63)
        val longestHost = "$label.$label.$label." + "b".repeat(61)
        assertEquals(253, longestHost.length)
        assertEquals("https://$longestHost", open(longestHost))
        val tooLongHost = "$label.$label.$label." + "b".repeat(63)
        assertEquals(search(tooLongHost), open(tooLongHost))
    }

    // Unicode

    @Test
    fun anInternationalizedDomainOpensOverHttps() {
        assertEquals("https://bücher.de", open("bücher.de"))
        assertEquals("https://xn--bcher-kva.de", open("xn--bcher-kva.de"))
        assertEquals("https://bücher.de/straße", open("https://bücher.de/straße"))
    }

    @Test
    fun unicodeTextIsSearchedForInUtf8() {
        assertEquals("https://www.google.com/search?q=%C4%91%C3%A0+n%E1%BA%B5ng", open("đà nẵng"))
    }

    // Control, bidi and invisible characters

    @Test
    fun controlBidiAndInvisibleCharactersNeverMakeAnAddress() {
        listOf(
            "exa\u0000mple.com",
            "example.com\u0000",
            "\u202Eexample.com",
            "example.com/\u202Efdp.apk",
            "https://example.com/\u202Efdp.apk",
            "exa\u200Bmple.com",
            "https://exa\u200Bmple.com",
            "example.com\u2066",
            "exam\u00ADple.com",
            "example.com\uFEFF",
            "example.com\uDB40\uDC01",
            "example.com\uD800",
            "java\u0000script:alert(1)",
            "http\u0085://example.com",
        ).forEach { input ->
            assertNull(input, omniboxAddress(input))
            assertEquals(input, search(input), open(input))
        }
    }

    @Test
    fun whiteSpaceInsideMakesText() {
        listOf("java\tscript:alert(1)", "example.com /path", "example\u00A0.com", "https://example.com/a b", "exa\u2028mple.com")
            .forEach { input ->
                assertNull(input, omniboxAddress(input))
                assertEquals(input, search(input), open(input))
            }
    }

    // Very long input

    @Test
    fun veryLongInputIsResolvedLikeShortInput() {
        val word = "a".repeat(100_000)
        assertEquals(search(word), open(word))
        val sentence = "heat pump ".repeat(10_000).trim()
        assertEquals(search(sentence), open(sentence))
        val longPath = "https://example.com/" + "a".repeat(50_000)
        assertEquals(longPath, open(longPath))
        val longHostLikePath = "example.com/" + "b".repeat(50_000)
        assertEquals("https://$longHostLikePath", open(longHostLikePath))
    }

    private fun open(input: String, engine: SearchEngine = SearchEngine.GOOGLE): String? = resolveOmnibox(input, engine)

    private fun search(text: String, engine: SearchEngine = SearchEngine.GOOGLE): String = engine.searchUrl(text)
}
