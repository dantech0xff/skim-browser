package com.dantech.skimbrowser.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun eachEngineBuildsItsResultsPage() {
        assertEquals("https://www.google.com/search?q=heat+pumps", SearchEngine.GOOGLE.searchUrl("heat pumps"))
        assertEquals("https://www.bing.com/search?q=heat+pumps", SearchEngine.BING.searchUrl("heat pumps"))
        assertEquals("https://duckduckgo.com/?q=heat+pumps", SearchEngine.DUCKDUCKGO.searchUrl("heat pumps"))
        assertEquals("https://www.ecosia.org/search?q=heat+pumps", SearchEngine.ECOSIA.searchUrl("heat pumps"))
    }

    @Test
    fun eachEngineBuildsItsSuggestionsEndpoint() {
        assertEquals("https://www.google.com/complete/search?client=firefox&q=osaka", SearchEngine.GOOGLE.suggestUrl("osaka"))
        assertEquals("https://www.bing.com/osjson.aspx?query=osaka&form=OSDJAS", SearchEngine.BING.suggestUrl("osaka"))
        assertEquals("https://ac.duckduckgo.com/ac/?q=osaka&type=list", SearchEngine.DUCKDUCKGO.suggestUrl("osaka"))
        assertEquals("https://ac.ecosia.org/autocomplete?q=osaka&type=list", SearchEngine.ECOSIA.suggestUrl("osaka"))
    }

    @Test
    fun theQueryIsFormEncodedAsUtf8() {
        assertEquals(
            "https://www.google.com/search?q=a%26b%3Dc+%231+%3F%2F%2B%25",
            SearchEngine.GOOGLE.searchUrl("a&b=c #1 ?/+%"),
        )
        assertEquals(
            "https://www.bing.com/osjson.aspx?query=%E1%BB%92saka+%E6%9D%B1%E4%BA%AC&form=OSDJAS",
            SearchEngine.BING.suggestUrl("Ồsaka 東京"),
        )
    }

    @Test
    fun aQueryCannotAddParametersOrPlaceholders() {
        val url = SearchEngine.DUCKDUCKGO.searchUrl("{q}&kp=-2")

        assertEquals("https://duckduckgo.com/?q=%7Bq%7D%26kp%3D-2", url)
    }

    @Test
    fun everyEndpointIsHttpsAndTakesTheQueryOnce() {
        SearchEngine.entries.forEach { engine ->
            listOf(engine.searchTemplate, engine.suggestTemplate).forEach { template ->
                assertTrue(template, template.startsWith("https://"))
                assertEquals(template, 1, template.split("{q}").size - 1)
            }
        }
    }
}
