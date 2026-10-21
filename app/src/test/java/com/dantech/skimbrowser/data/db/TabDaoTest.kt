package com.dantech.skimbrowser.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.dao.TabDao
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.data.db.entity.TabEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupEntity
import com.dantech.skimbrowser.data.db.entity.TabGroupingSnapshotEntity
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabDaoTest {

    private lateinit var db: SkimDatabase
    private lateinit var dao: TabDao

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        dao = db.tabDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun openTabsSkipClosedTabsAndListUngroupedFirstThenByGroupAndPosition() = runTest {
        val first = dao.insertGroup(TabGroupEntity(title = "Heat pump research", position = 0))
        val second = dao.insertGroup(TabGroupEntity(title = "Osaka", position = 1))
        dao.insert(tab("osaka-2", position = 1, groupId = second))
        dao.insert(tab("heat-1", position = 0, groupId = first))
        dao.insert(tab("other", position = 0))
        dao.insert(tab("osaka-1", position = 0, groupId = second))
        dao.insert(tab("closed", position = 2, groupId = first, closedAt = 50))

        assertEquals(listOf("other", "heat-1", "osaka-1", "osaka-2"), dao.openTabs().map { it.id })
    }

    @Test
    fun deleteAllClosedRemovesOnlyClosedTabs() = runTest {
        dao.insert(tab("open", position = 0))
        dao.insert(tab("closed", position = 1, closedAt = 50))

        dao.deleteAllClosed()

        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM tabs"))
        assertEquals(listOf("open"), dao.openTabs().map { it.id })
    }

    @Test
    fun clearMissingChatIdsKeepsTheIdsOfSavedChats() = runTest {
        val saved = db.skimChatDao().insertChat(
            SkimChatEntity(kind = ChatKind.PAGE, url = "https://kotlinlang.org/", host = "kotlinlang.org", title = "Kotlin", createdAt = 1, updatedAt = 1),
        )
        dao.insert(tab("with-chat", position = 0, chatId = saved))
        dao.insert(tab("orphan", position = 1, chatId = saved + 100))

        dao.clearMissingChatIds()

        val chats = dao.openTabs().associate { it.id to it.chatId }
        assertEquals(saved, chats["with-chat"])
        assertNull(chats["orphan"])
    }

    @Test
    fun deleteGroupsWithoutTabsKeepsGroupsThatStillHoldATab() = runTest {
        val used = dao.insertGroup(TabGroupEntity(title = "Used", position = 0))
        val empty = dao.insertGroup(TabGroupEntity(title = "Empty", position = 1))
        dao.insert(tab("grouped", position = 0, groupId = used))
        // An ungrouped tab puts a NULL into the subquery, which must not stop the delete.
        dao.insert(tab("ungrouped", position = 0))

        dao.deleteGroupsWithoutTabs()

        assertEquals(listOf(used), dao.groups().map { it.id })
        assertTrue(empty !in dao.groupIds())
    }

    @Test
    fun deletingAGroupLeavesItsTabsUngrouped() = runTest {
        val group = dao.insertGroup(TabGroupEntity(title = "Heat pump research", position = 0))
        dao.insert(tab("a", position = 0, groupId = group))

        dao.deleteGroup(group)

        assertNull(dao.openTabs().single().groupId)
    }

    @Test
    fun deleteClosedLeavesAReopenedTabAlone() = runTest {
        dao.insert(tab("undone", position = 0))
        dao.insert(tab("closed", position = 1))
        dao.markClosed(listOf("undone", "closed"), at = 50)
        dao.reopen(listOf("undone"))

        dao.deleteClosed(listOf("undone", "closed"))

        assertEquals(listOf("undone"), dao.openTabs().map { it.id })
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM tabs"))
    }

    @Test
    fun placeMovesAStoredTabAndNeverCreatesOne() = runTest {
        val group = dao.insertGroup(TabGroupEntity(title = "Osaka", position = 0))
        dao.insert(tab("a", position = 3))

        dao.place("a", group, 0)
        dao.place("not-stored", group, 1)

        val stored = dao.openTabs()
        assertEquals(listOf("a"), stored.map { it.id })
        assertEquals(group, stored.single().groupId)
        assertEquals(0, stored.single().position)
    }

    @Test
    fun snapshotTableKeepsASingleRow() = runTest {
        dao.putSnapshot(snapshot(json = "[\"first\"]"))
        dao.putSnapshot(snapshot(json = "[\"second\"]"))
        dao.setSnapshotBanner(false)

        val stored = dao.snapshot()
        assertEquals("[\"second\"]", stored?.previousLayoutJson)
        assertEquals(false, stored?.bannerVisible)
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM tab_grouping_snapshot"))

        dao.clearSnapshot()
        assertNull(dao.snapshot())
    }

    private fun tab(id: String, position: Int, groupId: Long? = null, chatId: Long? = null, closedAt: Long? = null) =
        TabEntity(
            id = id,
            url = "https://example.com/$id",
            title = id,
            groupId = groupId,
            position = position,
            chatId = chatId,
            createdAt = 1,
            lastAccessedAt = 1,
            closedAt = closedAt,
        )

    private fun snapshot(json: String) = TabGroupingSnapshotEntity(
        previousLayoutJson = json,
        tabSetKey = "key",
        appliedLayoutVersion = 2,
        bannerVisible = true,
        createdAt = 10,
    )
}
