package com.dantech.skimbrowser.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardCaptureTest {

    @Test
    fun aBoardGoldenIsNamedAfterItsDesignPng() {
        assertEquals(
            "src/test/screenshots/boards/05-browsing--loading.png",
            boardGoldenPath("05-browsing--loading"),
        )
    }

    @Test
    fun aNameWithoutADesignPngIsRejected() {
        // A single dash instead of the double dash of a board state.
        assertThrows(IllegalArgumentException::class.java) { boardGoldenPath("05-browsing-loading") }
    }

    @Test
    fun aPathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { boardGoldenPath("../screens/05-browsing") }
        assertThrows(IllegalArgumentException::class.java) { boardGoldenPath("") }
    }
}
