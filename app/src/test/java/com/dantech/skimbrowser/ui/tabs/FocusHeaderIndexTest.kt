package com.dantech.skimbrowser.ui.tabs

import com.dantech.skimbrowser.browser.TabGroup
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The grid index Tabs scrolls to when a link names a tab. */
class FocusHeaderIndexTest {

    @Test
    fun theHeaderOfTheSectionComesBeforeItsCards() {
        val state = grouped()

        assertEquals(1, focusHeaderIndex(state, "a", showTopBanner = true))
        assertEquals(4, focusHeaderIndex(state, "c", showTopBanner = true))
        assertEquals(0, focusHeaderIndex(state, "a", showTopBanner = false))
        assertNull(focusHeaderIndex(state, "missing", showTopBanner = false))
    }

    @Test
    fun anUngroupedGridScrollsToTheCard() {
        val state = TabsUiState(
            sections = persistentListOf(
                TabSection(null, persistentListOf(card("a"), card("b")), showHeader = false),
            ),
        )

        assertEquals(0, focusHeaderIndex(state, "b", showTopBanner = false))
        assertEquals(1, focusHeaderIndex(state, "b", showTopBanner = true))
    }

    private fun grouped() = TabsUiState(
        sections = persistentListOf(
            TabSection(TabGroup(1, "Heat", 0), persistentListOf(card("a"), card("b")), showHeader = true),
            TabSection(null, persistentListOf(card("c")), showHeader = true),
        ),
    )

    private fun card(id: String) = TabCardState(id, id, "A", isCurrent = false, hasSkimChat = false)
}
