package com.dantech.skimbrowser.domain.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStripTest {

    @Test
    fun aSwipeStaysInsideTheGroupThenCrossesIntoTheNextOne() {
        val research = 1L
        val trip = 2L
        val state = TabsState(
            groups = listOf(TabGroup(research, "Heat pump research", 0), TabGroup(trip, "Osaka trip", 1)),
            tabs = listOf(
                tab("a", research, 0),
                tab("b", research, 1),
                tab("c", trip, 0),
                tab("d", null, 0),
            ),
            currentTabId = "b",
        )

        val intoGroup = state.adjacentTab(forward = true)
        assertEquals("c", intoGroup?.id)
        assertEquals(trip, intoGroup?.groupId)
        assertTrue(intoGroup!!.changedGroup)

        val inside = state.copy(currentTabId = "a").adjacentTab(forward = true)
        assertEquals("b", inside?.id)
        assertFalse(inside!!.changedGroup)

        val intoOther = state.copy(currentTabId = "c").adjacentTab(forward = true)
        assertEquals("d", intoOther?.id)
        assertNull(intoOther?.groupId)
        assertTrue(intoOther!!.changedGroup)
    }

    @Test
    fun theEndsOfTheStripAndTheOtherKindDoNotMove() {
        val state = TabsState(
            tabs = listOf(tab("a", null, 0), tab("b", null, 1), tab("secret", null, 0, incognito = true)),
            currentTabId = "a",
        )

        assertNull(state.adjacentTab(forward = false))
        assertEquals("b", state.adjacentTab(forward = true)?.id)
        assertNull(state.copy(currentTabId = "b").adjacentTab(forward = true))
        assertNull(state.copy(currentTabId = "secret").adjacentTab(forward = true))
        assertNull(TabsState().adjacentTab(forward = true))
    }

    private fun tab(id: String, groupId: Long?, position: Int, incognito: Boolean = false) = Tab(
        id = id,
        incognito = incognito,
        url = if (id == "d") null else "https://example.com/$id",
        title = if (id == "d") "" else "Title $id",
        groupId = groupId,
        position = position,
        createdAt = 0,
        lastAccessedAt = 0,
    )
}
