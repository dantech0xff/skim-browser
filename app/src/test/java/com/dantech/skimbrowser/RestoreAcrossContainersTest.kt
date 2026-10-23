package com.dantech.skimbrowser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.GroupLayout
import com.dantech.skimbrowser.browser.RestoreResult
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.data.db.entity.SkimChatEntity
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * A new container on the same directory is the app after process death. Normal tabs, their groups,
 * the current tab and a saved chat come back. An incognito tab does not, and a chat id that existed
 * only in memory is not stored.
 */
@RunWith(AndroidJUnit4::class)
class RestoreAcrossContainersTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val main = MainDispatcherRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun normalTabsGroupsAndASavedChatComeBack() = runTest {
        val storage = AppStorage.temp(folder.newFolder())
        lateinit var heat: String
        lateinit var sizing: String
        lateinit var notes: String
        var chatId = 0L

        AppContainer(app, storage).use { container ->
            val tabs = container.tabManager
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
            chatId = container.chatRepository.createChat(
                SkimChatEntity(
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
            if (container.incognitoSession.isSupported) {
                tabs.newTab(incognito = true, url = SECRET)
            }
            container.settings.setLastTabId(heat)
            tabs.awaitWrites()
        }

        AppContainer(app, storage).use { container ->
            val restored = container.tabManager.restore()
            assertTrue(restored is RestoreResult.Restored)
            assertEquals(3, (restored as RestoreResult.Restored).tabCount)
            val state = container.tabManager.state.value
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
