package com.dantech.skimbrowser.data.repo

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.domain.tab.GroupingSnapshot
import com.dantech.skimbrowser.domain.tab.PersistedTab
import com.dantech.skimbrowser.domain.tab.PersistedTabGroup
import com.dantech.skimbrowser.testing.assertThrowsSuspending
import com.dantech.skimbrowser.testing.execRaw
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
class TabRepositoryTest {

    private lateinit var db: SkimDatabase
    private lateinit var tabs: TabRepository

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        tabs = TabRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anInMemoryChatIdIsStoredAsNoChat() = runTest {
        val saved = savedChat()
        tabs.upsert(tab("negative", position = 0, chatId = -3))
        tabs.upsert(tab("zero", position = 1, chatId = 0))
        tabs.upsert(tab("saved", position = 2, chatId = saved))

        // Checked in the table itself: loadOpen would hide a stored negative id.
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM tabs WHERE chatId <= 0"))
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM tabs WHERE chatId = $saved"))
        val chats = tabs.loadOpen().first.associate { it.id to it.chatId }
        assertNull(chats["negative"])
        assertNull(chats["zero"])
        assertEquals(saved, chats["saved"])
    }

    @Test
    fun upsertReplacesTheStoredTab() = runTest {
        tabs.upsert(tab("a", position = 0))
        tabs.upsert(tab("a", position = 4).copy(url = "https://kotlinlang.org/", title = "Kotlin", desktopSite = true))

        val stored = tabs.loadOpen().first.single()
        assertEquals("https://kotlinlang.org/", stored.url)
        assertEquals("Kotlin", stored.title)
        assertEquals(4, stored.position)
        assertTrue(stored.desktopSite)
    }

    @Test
    fun aTabOfAGroupThatIsGoneIsStoredUngrouped() = runTest {
        tabs.upsert(tab("a", position = 0, groupId = 77))

        assertNull(tabs.loadOpen().first.single().groupId)
    }

    @Test
    fun undoOfAClosedTabBringsItBackWithItsIdPositionAndGroup() = runTest {
        val group = tabs.saveLayout(listOf(PersistedTabGroup(title = "Heat pump research", position = 0)), emptyList()).single()
        tabs.upsert(tab("a", position = 0, groupId = group))
        tabs.upsert(tab("b", position = 1, groupId = group))
        tabs.upsert(tab("c", position = 2, groupId = group))

        tabs.markClosed(listOf("b"), at = 50)
        tabs.reopen(listOf("b"))

        val restored = tabs.loadOpen().first
        assertEquals(listOf("a", "b", "c"), restored.map { it.id })
        assertEquals(listOf(0, 1, 2), restored.map { it.position })
        assertTrue(restored.all { it.groupId == group && it.closedAt == null })
    }

    @Test
    fun undoOfSeveralClosedTabsBringsThemAllBackInOrder() = runTest {
        (0 until 7).forEach { tabs.upsert(tab("t$it", position = it)) }

        tabs.markClosed((0 until 7).map { "t$it" }, at = 50)
        tabs.reopen((0 until 7).map { "t$it" })

        assertEquals((0 until 7).map { "t$it" }, tabs.loadOpen().first.map { it.id })
    }

    @Test
    fun aTabStillClosedWhenTheProcessDiesIsNotRestored() = runTest {
        tabs.upsert(tab("kept", position = 0))
        tabs.upsert(tab("closed", position = 1))
        tabs.markClosed(listOf("closed"), at = 50)

        assertEquals(listOf("kept"), tabs.loadOpen().first.map { it.id })
        tabs.reopen(listOf("closed"))
        assertEquals(listOf("kept"), tabs.loadOpen().first.map { it.id })
    }

    @Test
    fun deleteClosedMakesTheCloseFinal() = runTest {
        tabs.upsert(tab("a", position = 0))
        tabs.markClosed(listOf("a"), at = 50)

        tabs.deleteClosed(listOf("a"))
        tabs.reopen(listOf("a"))

        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM tabs"))
    }

    @Test
    fun loadOpenForgetsChatsThatWereDeletedAndGroupsLeftEmpty() = runTest {
        val chat = savedChat()
        val groups = tabs.saveLayout(
            listOf(PersistedTabGroup(title = "Kept", position = 0), PersistedTabGroup(title = "Emptied", position = 1)),
            emptyList(),
        )
        tabs.upsert(tab("a", position = 0, groupId = groups[0], chatId = chat))
        tabs.upsert(tab("b", position = 0, groupId = groups[1]))
        tabs.markClosed(listOf("b"), at = 50)
        db.skimChatDao().deleteChat(chat)

        val (open, openGroups) = tabs.loadOpen()

        assertNull(open.single().chatId)
        assertEquals(listOf(groups[0]), openGroups.map { it.id })
    }

    @Test
    fun saveLayoutCreatesGroupsAndPutsTabsInThemInOneCall() = runTest {
        listOf("a", "b", "c", "d").forEachIndexed { index, id -> tabs.upsert(tab(id, position = index)) }

        val ids = tabs.saveLayout(
            groups = listOf(
                PersistedTabGroup(title = "Heat pump research", position = 0, createdBySkim = true),
                PersistedTabGroup(title = "Osaka trip", position = 1, createdBySkim = true),
            ),
            placements = listOf(
                TabPlacement("a", groupIndex = 0, position = 0),
                TabPlacement("c", groupIndex = 0, position = 1),
                TabPlacement("b", groupIndex = 1, position = 0),
                TabPlacement("d", groupIndex = null, position = 0),
            ),
        )

        val (open, groups) = tabs.loadOpen()
        assertEquals(2, ids.size)
        assertTrue(ids.all { it > 0 })
        assertEquals(listOf("Heat pump research", "Osaka trip"), groups.map { it.title })
        assertEquals(ids, groups.map { it.id })
        assertTrue(groups.all { it.createdBySkim })
        val placed = open.associateBy { it.id }
        assertEquals(ids[0], placed.getValue("a").groupId)
        assertEquals(ids[0], placed.getValue("c").groupId)
        assertEquals(1, placed.getValue("c").position)
        assertEquals(ids[1], placed.getValue("b").groupId)
        assertNull(placed.getValue("d").groupId)
    }

    @Test
    fun saveLayoutUpdatesListedGroupsAndDeletesTheOthers() = runTest {
        val created = tabs.saveLayout(
            listOf(PersistedTabGroup(title = "Heat pump research", position = 0), PersistedTabGroup(title = "Osaka", position = 1)),
            emptyList(),
        )
        tabs.upsert(tab("a", position = 0, groupId = created[0]))
        tabs.upsert(tab("b", position = 0, groupId = created[1]))

        val ids = tabs.saveLayout(
            groups = listOf(PersistedTabGroup(id = created[0], title = "Heat pumps", position = 0)),
            placements = listOf(TabPlacement("a", groupIndex = 0, position = 0)),
        )

        val (open, groups) = tabs.loadOpen()
        assertEquals(listOf(created[0]), ids)
        assertEquals(listOf("Heat pumps"), groups.map { it.title })
        assertNull(open.single { it.id == "b" }.groupId)
    }

    @Test
    fun saveLayoutKeepsAGroupIdChosenByTheCaller() = runTest {
        tabs.upsert(tab("a", position = 0))

        val ids = tabs.saveLayout(
            groups = listOf(PersistedTabGroup(id = 77, title = "Osaka", position = 0)),
            placements = listOf(TabPlacement("a", groupIndex = 0, position = 0)),
        )

        assertEquals(listOf(77L), ids)
        assertEquals(77L, tabs.loadOpen().first.single().groupId)
    }

    @Test
    fun saveLayoutWithASnapshotWritesEverythingOrNothing() = runTest {
        listOf("a", "b").forEachIndexed { index, id -> tabs.upsert(tab(id, position = index)) }
        val before = tabs.loadOpen()
        // Make the last write of the layout, the snapshot, fail inside the transaction.
        db.execRaw(
            "CREATE TEMP TRIGGER fail_snapshot BEFORE INSERT ON tab_grouping_snapshot " +
                "BEGIN SELECT RAISE(ABORT, 'snapshot write failed'); END",
        )

        assertThrowsSuspending<SQLiteConstraintException> {
            tabs.saveLayout(
                groups = listOf(PersistedTabGroup(title = "Heat pump research", position = 0, createdBySkim = true)),
                placements = listOf(TabPlacement("b", groupIndex = 0, position = 0), TabPlacement("a", groupIndex = 0, position = 1)),
                snapshot = snapshot(),
            )
        }

        assertEquals(before, tabs.loadOpen())
        assertNull(tabs.snapshot())
    }

    @Test
    fun saveLayoutStoresTheSnapshotAsTheSingleRow() = runTest {
        tabs.upsert(tab("a", position = 0))

        tabs.saveLayout(
            groups = listOf(PersistedTabGroup(title = "Heat pump research", position = 0, createdBySkim = true)),
            placements = listOf(TabPlacement("a", groupIndex = 0, position = 0)),
            snapshot = snapshot().copy(id = 9),
        )
        tabs.setSnapshotBanner(false)

        assertEquals(snapshot().copy(bannerVisible = false), tabs.snapshot())
        tabs.clearSnapshot()
        assertNull(tabs.snapshot())
    }

    @Test
    fun anInvalidLayoutIsRejectedBeforeAnythingIsWritten() = runTest {
        tabs.upsert(tab("a", position = 0))
        val group = PersistedTabGroup(title = "Osaka", position = 0)

        assertThrowsSuspending<IllegalArgumentException> {
            tabs.saveLayout(listOf(group), listOf(TabPlacement("a", 0, 0), TabPlacement("a", null, 1)))
        }
        assertThrowsSuspending<IllegalArgumentException> {
            tabs.saveLayout(listOf(group), listOf(TabPlacement("a", 1, 0)))
        }
        assertThrowsSuspending<IllegalArgumentException> {
            tabs.saveLayout(listOf(group.copy(id = 5), group.copy(id = 5)), emptyList())
        }
        assertThrowsSuspending<IllegalArgumentException> {
            tabs.saveLayout(listOf(group.copy(id = -2)), emptyList())
        }

        val (open, groups) = tabs.loadOpen()
        assertEquals(emptyList<PersistedTabGroup>(), groups)
        assertNull(open.single().groupId)
    }

    @Test
    fun placementsNeverStoreATabThatIsNotStored() = runTest {
        tabs.saveLayout(
            groups = listOf(PersistedTabGroup(title = "Osaka", position = 0)),
            placements = listOf(TabPlacement("incognito-tab", groupIndex = 0, position = 0)),
        )

        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM tabs"))
    }

    @Test
    fun closingMoreTabsThanOneStatementCanBindStillCloses() = runTest {
        val ids = (0 until 1200).map { "tab-$it" }
        db.withTransaction { ids.forEachIndexed { index, id -> db.tabDao().insert(tab(id, position = index).toEntity()) } }

        tabs.markClosed(ids, at = 50)
        assertEquals(1200L, db.longQuery("SELECT COUNT(*) FROM tabs WHERE closedAt = 50"))
        tabs.reopen(ids)
        assertEquals(1200L, db.longQuery("SELECT COUNT(*) FROM tabs WHERE closedAt IS NULL"))
        tabs.markClosed(ids, at = 60)
        tabs.deleteClosed(ids)
        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM tabs"))
    }

    private suspend fun savedChat(): Long = db.skimChatDao().insertChat(
        SkimChatEntity(kind = ChatKind.PAGE, url = "https://kotlinlang.org/", host = "kotlinlang.org", title = "Kotlin", createdAt = 1, updatedAt = 1),
    )

    private fun tab(id: String, position: Int, groupId: Long? = null, chatId: Long? = null) = PersistedTab(
        id = id,
        url = "https://example.com/$id",
        title = id,
        groupId = groupId,
        position = position,
        chatId = chatId,
        createdAt = 1,
        lastAccessedAt = 1,
    )

    private fun snapshot() = GroupingSnapshot(
        previousLayoutJson = "{\"groups\":[]}",
        tabSetKey = "a,b",
        appliedLayoutVersion = 3,
        bannerVisible = true,
        createdAt = 20,
    )
}
