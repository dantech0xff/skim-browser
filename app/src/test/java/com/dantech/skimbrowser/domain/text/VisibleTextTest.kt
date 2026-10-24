package com.dantech.skimbrowser.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Visible text drops hidden characters and cuts on a character boundary. */
class VisibleTextTest {

    @Test
    fun cutToKeepsAShortStringAndStopsBeforeASurrogate() {
        assertEquals("Heat", "Heat".cutTo(8))
        assertEquals("", "Heat".cutTo(0))
        val emoji = "A\uD83D\uDE00B"
        assertEquals("A", emoji.cutTo(2))
        assertEquals("A\uD83D\uDE00", emoji.cutTo(3))
    }

    @Test
    fun toVisibleTextDropsHiddenCharactersAndCollapsesSpace() {
        val hidden = "Invoice\u202Efdp.apk\n\n  next"
        assertEquals("Invoicefdp.apk next", hidden.toVisibleText())
        assertEquals("ab", "a\u200Bb".toVisibleText())
    }
}
