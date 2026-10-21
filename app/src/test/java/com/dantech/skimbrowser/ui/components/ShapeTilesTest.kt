package com.dantech.skimbrowser.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShapeTilesTest {

    @Test
    fun theLetterComesFromTheHostWithoutWww() {
        assertEquals("D", siteLetter(host = "www.daylightjournal.com", title = "Why heat pumps are winning"))
        assertEquals("T", siteLetter(host = "tabi-note.jp", title = "Osaka night buses"))
        assertEquals("E", siteLetter(host = "WWW.Example.com", title = null))
    }

    @Test
    fun aShortcutUsesItsName() {
        // Hacker News lives on news.ycombinator.com, but its tile reads H.
        assertEquals("H", siteLetter(host = null, title = "Hacker News"))
        assertEquals("G", siteLetter(host = "  ", title = "GitHub"))
    }

    @Test
    fun internationalizedHostsAreDecoded() {
        assertEquals("B", siteLetter(host = "xn--bcher-kva.example", title = null))
    }

    @Test
    fun theFirstLetterOrDigitIsUsed() {
        assertEquals("1", siteLetter(host = "192.168.1.1", title = null))
        assertEquals("U", siteLetter(host = null, title = "  (untitled) page"))
        assertEquals("É", siteLetter(host = null, title = "école"))
    }

    @Test
    fun nothingToShowGivesAnEmptyLetter() {
        assertEquals("", siteLetter(host = null, title = null))
        assertEquals("", siteLetter(host = null, title = "…"))
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun shortcutShapesFollowTheDrawnOrderAndRepeat() {
        val drawn = listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.SoftBurst,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Flower,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.Pentagon,
        )

        drawn.forEachIndexed { position, shape -> assertSame(shape, shortcutShapeFor(position)) }
        assertSame(MaterialShapes.Cookie9Sided, shortcutShapeFor(7))
        assertSame(MaterialShapes.Pentagon, shortcutShapeFor(13))
    }
}
