package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryStepUrlTest {

    private val pages = listOf(
        "https://news.example/first",
        "https://shop.example/second",
        "about:blank",
    )

    @Test
    fun backUsesThePreviousWebPage() {
        assertEquals("https://news.example/first", historyStepUrl(1, pages.size, pages::get, -1))
    }

    @Test
    fun forwardSkipsANonWebEntry() {
        assertNull(historyStepUrl(1, pages.size, pages::get, 1))
    }

    @Test
    fun theEndsOfHistoryHaveNoNeighbor() {
        assertNull(historyStepUrl(0, pages.size, pages::get, -1))
        assertNull(historyStepUrl(2, pages.size, pages::get, 1))
    }
}
