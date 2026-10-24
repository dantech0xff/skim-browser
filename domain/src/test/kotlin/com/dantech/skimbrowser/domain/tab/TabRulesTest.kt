package com.dantech.skimbrowser.domain.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabRulesTest {

    @Test
    fun incognitoTabsAreNeverRestored() {
        val saved = listOf(tab("normal"), tab("private", incognito = true), tab("other"))
        assertEquals(listOf("normal", "other"), tabsToRestore(saved).map { it.id })
    }

    @Test
    fun undoRestoresIncognitoTabsOnlyWhileTheSessionIsOpen() {
        val closing = listOf(tab("normal"), tab("private", incognito = true))

        val open = partitionUndoClose(closing, sessionOpen = true)
        assertEquals(listOf("normal", "private"), open.restorable.map { it.id })
        assertTrue(open.lost.isEmpty())

        val ended = partitionUndoClose(closing, sessionOpen = false)
        assertEquals(listOf("normal"), ended.restorable.map { it.id })
        assertEquals(listOf("private"), ended.lost.map { it.id })
    }

    @Test
    fun closingTheLastIncognitoTabEndsTheSession() {
        val privateTab = tab("private", incognito = true)
        val normal = tab("normal")
        assertTrue(closesLastIncognitoTab(listOf(privateTab), listOf(normal)))
        assertFalse(closesLastIncognitoTab(listOf(privateTab), listOf(normal, tab("still", incognito = true))))
        assertFalse(closesLastIncognitoTab(listOf(normal), emptyList()))
    }

    @Test
    fun aRestoredTabWhoseGroupIsGoneGoesToTheEndOfOther() {
        val kept = tab("kept", groupId = 1, position = 0)
        val orphan = tab("orphan", groupId = 9, position = 3)
        val ungrouped = tab("loose", groupId = null, position = 1)

        val placed = placeRestoredTabs(listOf(kept, orphan, ungrouped), existingGroupIds = setOf(1L), firstOtherPosition = 4)

        assertEquals(1L, placed[0].groupId)
        assertEquals(0, placed[0].position)
        assertNull(placed[1].groupId)
        assertEquals(4, placed[1].position)
        assertNull(placed[2].groupId)
        assertEquals(1, placed[2].position)
    }

    @Test
    fun twoOrphansTakeTheNextOtherPositionsInOrder() {
        val placed = placeRestoredTabs(
            listOf(tab("a", groupId = 3), tab("b", groupId = 4)),
            existingGroupIds = emptySet(),
            firstOtherPosition = 0,
        )
        assertEquals(listOf(0, 1), placed.map { it.position })
        assertTrue(placed.all { it.groupId == null })
    }

    @Test
    fun undoSelectsThePreviousTabWhenNothingElseWasChosen() {
        val restored = listOf(tab("a", lastAccessedAt = 1), tab("b", lastAccessedAt = 5))
        val ids = restored.map { it.id }.toSet()
        assertEquals(
            "b",
            currentAfterUndo(
                restoredIds = ids,
                previousCurrentId = "b",
                currentTabId = "replacement",
                replacementId = "replacement",
                restored = restored,
            ),
        )
    }

    @Test
    fun undoKeepsATabThePersonSelectedAfterTheClose() {
        val restored = listOf(tab("a", lastAccessedAt = 1))
        assertEquals(
            "chosen",
            currentAfterUndo(
                restoredIds = setOf("a"),
                previousCurrentId = "a",
                currentTabId = "chosen",
                replacementId = "replacement",
                restored = restored,
            ),
        )
    }

    @Test
    fun undoSelectsTheMostRecentlyUsedTabWhenNothingIsCurrent() {
        val restored = listOf(tab("old", lastAccessedAt = 1), tab("new", lastAccessedAt = 9))
        assertEquals(
            "new",
            currentAfterUndo(
                restoredIds = setOf("old", "new"),
                previousCurrentId = "gone",
                currentTabId = null,
                replacementId = null,
                restored = restored,
            ),
        )
    }

    @Test
    fun askCandidatesAreNormalWebPages() {
        val state = TabsState(
            tabs = listOf(
                tab("page", url = "https://example.com/a"),
                tab("blank", url = null),
                tab("about", url = "about:blank"),
                tab("private", url = "https://secret.example/", incognito = true),
                tab("http", url = "http://notes.example/"),
            ),
        )
        assertEquals(2, pagesEligibleForAsk(state))
        assertTrue(state.tab("page")!!.isAskCandidate())
        assertFalse(state.tab("blank")!!.isAskCandidate())
        assertFalse(state.tab("private")!!.isAskCandidate())
    }

    private fun tab(
        id: String,
        incognito: Boolean = false,
        groupId: Long? = null,
        position: Int = 0,
        url: String? = "https://example.com/",
        lastAccessedAt: Long = 0,
    ) = Tab(
        id = id,
        incognito = incognito,
        url = url,
        title = "",
        groupId = groupId,
        position = position,
        createdAt = 0,
        lastAccessedAt = lastAccessedAt,
    )
}
