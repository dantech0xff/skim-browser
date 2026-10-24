package com.dantech.skimbrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.RestoreResult
import com.dantech.skimbrowser.testing.MainDispatcherRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A new container on the same directory is the app after process death. Normal tabs, their groups,
 * the current tab and a saved chat come back. An incognito tab does not, and a chat id that existed
 * only in memory is not stored.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RestoreAcrossContainersTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @get:Rule(order = 2)
    val main = MainDispatcherRule()

    @Inject lateinit var graph: ProcessGraph

    @Before
    fun setUp() {
        TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        graph.close()
        TestStorage.current.set(null)
    }

    @Test
    fun normalTabsGroupsAndASavedChatComeBack() = runTest {
        lateinit var heat: String
        lateinit var sizing: String
        lateinit var notes: String
        var chatId = 0L

        run {
            val tabs = graph.tabManager()
            heat = tabs.newTab(incognito = false, url = HEAT)
            sizing = tabs.newTab(incognito = false, url = SIZING)
            notes = tabs.newTab(incognito = false, url = NOTES)
            assertTrue(
                tabs.applyLayout(
                    GroupLayout(
                        groups = listOf(GroupLayout.Group(null, "Heat pump research", listOf(heat, sizing))),
                        ungrouped = listOf(notes),
                    ),
                ),
            )
            tabs.select(heat)
            chatId = graph.chatRepository().createChat(
                SavedChat(
                    kind = ChatKind.PAGE,
                    url = HEAT,
                    host = "daylightjournal.com",
                    title = "Why heat pumps are winning",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            tabs.bindChat(heat, chatId)
            tabs.bindChat(notes, -4)
            if (graph.incognitoSession().isSupported) {
                tabs.newTab(incognito = true, url = SECRET)
            }
            graph.settings().setLastTabId(heat)
            tabs.awaitWrites()
        }
        graph.close()

        run {
            val restored = graph.tabManager().restore()
            assertTrue(restored is RestoreResult.Restored)
            assertEquals(3, (restored as RestoreResult.Restored).tabCount)
            val state = graph.tabManager().state.value
            assertTrue(state.tabs.none { it.incognito })
            assertEquals(setOf(heat, sizing, notes), state.tabs.map { it.id }.toSet())
            assertEquals(heat, state.currentTabId)
            assertEquals("Heat pump research", state.groups.single().title)
            assertEquals(listOf(heat, sizing), state.tabs.filter { it.groupId != null }.map { it.id })
            assertEquals(chatId, state.tabs.single { it.id == heat }.chatId)
            assertNull(state.tabs.single { it.id == notes }.chatId)
        }
    }

    private companion object {
        const val HEAT = "https://daylightjournal.com/energy/heat-pumps-cold"
        const val SIZING = "https://heatcalc.example/sizing"
        const val NOTES = "https://kotlinlang.org/docs/releases.html"
        const val SECRET = "https://secret.example/inbox"
    }
}
