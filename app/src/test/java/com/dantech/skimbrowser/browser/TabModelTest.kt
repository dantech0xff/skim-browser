package com.dantech.skimbrowser.browser

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TabModelTest {

    @Test
    fun aLongPressTargetSurvivesSavedNavigationState() {
        val targets = listOf<HitTarget>(
            HitTarget.Link("https://example.com/a", text = "Read more"),
            HitTarget.Link("https://example.com/b", text = null),
            HitTarget.Image("https://example.com/i.png", alt = "Photo", linkUrl = "https://example.com/"),
        )

        targets.forEach { target ->
            val json = Json.encodeToString(HitTarget.serializer(), target)
            assertEquals(target, Json.decodeFromString(HitTarget.serializer(), json))
        }
        assertEquals(
            """{"type":"image","src":"https://example.com/i.png","alt":"Photo","linkUrl":"https://example.com/"}""",
            Json.encodeToString(HitTarget.serializer(), targets[2]),
        )
    }

    @Test
    fun aLayoutRoundTripsAsJson() {
        val layout = GroupLayout(
            groups = listOf(GroupLayout.Group(4, "Osaka trip", listOf("a", "b"), createdBySkim = true)),
            ungrouped = listOf("c"),
        )

        val json = Json.encodeToString(GroupLayout.serializer(), layout)

        assertEquals(layout, Json.decodeFromString(GroupLayout.serializer(), json))
    }

    @Test
    fun theCurrentLayoutListsNormalTabsOnlyAndSkipsEmptyGroups() {
        val state = TabsState(
            tabs = listOf(
                tab("a", groupId = 1, position = 0),
                tab("b", groupId = 1, position = 1),
                tab("c", groupId = null, position = 0),
                tab("lost", groupId = 9, position = 0),
                tab("private", groupId = null, position = 0, incognito = true),
            ),
            groups = listOf(TabGroup(1, "Trip", 0), TabGroup(2, "Waiting for Undo", 1)),
        )

        assertEquals(
            GroupLayout(listOf(GroupLayout.Group(1, "Trip", listOf("a", "b"))), ungrouped = listOf("c", "lost")),
            state.layout(),
        )
    }

    @Test
    fun theCurrentTabIsLookedUpById() {
        val state = TabsState(tabs = listOf(tab("a", null, 0), tab("b", null, 1)), currentTabId = "b")

        assertEquals("b", state.currentTab?.id)
        assertEquals(null, state.copy(currentTabId = "gone").currentTab)
    }

    private fun tab(id: String, groupId: Long?, position: Int, incognito: Boolean = false) = Tab(
        id = id,
        incognito = incognito,
        url = "https://$id.example/",
        title = id,
        groupId = groupId,
        position = position,
        createdAt = 0,
        lastAccessedAt = 0,
    )
}
