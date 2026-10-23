package com.dantech.skimbrowser.ui.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a dragged tab lands: same group only, nearest card when the drop is in a gap. */
class DragReorderTest {

    @Test
    fun aDropOnTheNextCardOfTheSameGroupTakesItsIndex() {
        val slots = listOf(slot("a", 1, 0, 0), slot("b", 1, 120, 0), slot("c", 2, 0, 160))

        assertEquals(1, targetIndexInGroup(slots, draggedKey = "a", groupId = 1, pointerX = 150, pointerY = 40))
    }

    @Test
    fun aDropOnAnotherGroupDoesNotMove() {
        val slots = listOf(slot("a", 1, 0, 0), slot("c", 2, 0, 160))

        assertNull(targetIndexInGroup(slots, draggedKey = "a", groupId = 1, pointerX = 20, pointerY = 180))
    }

    @Test
    fun aDropInTheGapLandsOnTheNearestCardOfTheSameGroup() {
        val slots = listOf(slot("a", 1, 0, 0), slot("b", 1, 200, 0))

        assertEquals(1, targetIndexInGroup(slots, draggedKey = "a", groupId = 1, pointerX = 160, pointerY = 40))
    }

    @Test
    fun otherIsItsOwnGroup() {
        val slots = listOf(slot("a", 1, 0, 0), slot("b", null, 0, 160), slot("c", null, 120, 160))

        assertEquals(1, targetIndexInGroup(slots, draggedKey = "b", groupId = null, pointerX = 150, pointerY = 180))
        assertNull(targetIndexInGroup(slots, draggedKey = "b", groupId = null, pointerX = 20, pointerY = 20))
    }

    private fun slot(key: String, groupId: Long?, x: Int, y: Int) = ReorderSlot(
        key = key,
        groupId = groupId,
        left = x,
        top = y,
        right = x + 100,
        bottom = y + 100,
    )
}
