package com.dantech.skimbrowser.ui.tabs

import com.dantech.skimbrowser.domain.tab.TabGroup
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The order the grid shows while a tab is dragged: only the dragged tab's section changes. */
class ReorderPreviewTest {

    private val sections = persistentListOf(
        section(TabGroup(id = 1, title = "Heat", position = 0), "a", "b", "c", "d"),
        section(TabGroup(id = 2, title = "Osaka", position = 1), "e"),
        section(null, "f", "g"),
    )

    @Test
    fun noPreviewShowsTheSectionsAsTheyAre() {
        assertSame(sections, previewSections(sections, null))
    }

    @Test
    fun aTabMovedWithinItsGroupTakesTheIndexAndTheOthersCloseUp() {
        val shown = previewSections(sections, ReorderPreview("b", 2))

        assertEquals(listOf("a", "c", "b", "d"), ids(shown[0]))
    }

    @Test
    fun aTabCanMoveToTheFirstAndTheLastPlace() {
        assertEquals(listOf("c", "a", "b", "d"), ids(previewSections(sections, ReorderPreview("c", 0))[0]))
        assertEquals(listOf("b", "c", "d", "a"), ids(previewSections(sections, ReorderPreview("a", 3))[0]))
    }

    @Test
    fun anIndexPastTheEndOfTheGroupStopsAtTheEnd() {
        assertEquals(listOf("b", "c", "d", "a"), ids(previewSections(sections, ReorderPreview("a", 9))[0]))
    }

    @Test
    fun aTabAtItsOwnIndexChangesNothing() {
        assertSame(sections, previewSections(sections, ReorderPreview("c", 2)))
    }

    @Test
    fun aGroupOfOneStaysAsItIs() {
        assertSame(sections, previewSections(sections, ReorderPreview("e", 1)))
    }

    @Test
    fun anIdThatIsInNoSectionChangesNothing() {
        assertSame(sections, previewSections(sections, ReorderPreview("gone", 0)))
    }

    @Test
    fun sectionsOtherThanTheDraggedOneAreUntouched() {
        val shown = previewSections(sections, ReorderPreview("g", 0))

        assertEquals(listOf("g", "f"), ids(shown[2]))
        assertSame(sections[0], shown[0])
        assertSame(sections[1], shown[1])
        assertEquals(sections[2].group, shown[2].group)
        assertEquals(sections[2].showHeader, shown[2].showHeader)
    }

    private fun ids(section: TabSection): List<String> = section.tabs.map { it.id }

    private fun section(group: TabGroup?, vararg ids: String) = TabSection(
        group = group,
        tabs = ids.map { TabCardState(it, "Tab $it", "T", isCurrent = false, hasSkimChat = false) }.toImmutableList(),
        showHeader = true,
    )
}
