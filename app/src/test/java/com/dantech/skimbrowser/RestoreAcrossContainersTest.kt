package com.dantech.skimbrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.data.db.entity.ChatKind
import com.dantech.skimbrowser.domain.chat.ChatRepository
import com.dantech.skimbrowser.domain.chat.SavedChat
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.GroupLayout
import com.dantech.skimbrowser.domain.tab.RestoreResult
import com.dantech.skimbrowser.testing.MainDispatcherRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * A new component on the same directory is the app after process death. Normal tabs, their groups,
 * the current tab and a saved chat come back. An incognito tab does not, and a chat id that existed
 * only in memory is not stored.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RestoreAcrossContainersTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    @Inject lateinit var resources: ReleaseProcessResources
    @Inject lateinit var tabs: TabManager
    @Inject lateinit var chats: ChatRepository
    @Inject lateinit var incognito: IncognitoSession
    @Inject lateinit var settings: SettingsRepository

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(folder.root, "shared").apply { mkdirs() }
        TestStorage.current.set(AppStorage.temp(dir))
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        resources.release()
        TestStorage.current.set(null)
    }

    @Test
    fun aWritesTabsGroupsAndASavedChat() = runTest {
        val heat = tabs.newTab(incognito = false, url = HEAT)
        val sizing = tabs.newTab(incognito = false, url = SIZING)
        val notes = tabs.newTab(incognito = false, url = NOTES)
        assertTrue(
            tabs.applyLayout(
                GroupLayout(
                    groups = listOf(GroupLayout.Group(null, "Heat pump research", listOf(heat, sizing))),
                    ungrouped = listOf(notes),
                ),
            ),
        )
        tabs.select(heat)
        val chatId = chats.createChat(
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
        if (incognito.isSupported) {
            tabs.newTab(incognito = true, url = SECRET)
        }
        settings.setLastTabId(heat)
        tabs.awaitWrites()
    }

    @Test
    fun bRestoresThemOnANewComponent() = runTest {
        assertTrue("aWritesTabsGroupsAndASavedChat did not run first", File(dir, "skim.db").isFile)

        val restored = tabs.restore()
        assertTrue(restored is RestoreResult.Restored)
        assertEquals(3, (restored as RestoreResult.Restored).tabCount)
        val state = tabs.state.value
        assertTrue(state.tabs.none { it.incognito })
        assertTrue(state.tabs.none { it.url == SECRET })
        assertEquals(setOf(HEAT, SIZING, NOTES), state.tabs.map { it.url }.toSet())
        assertEquals(HEAT, state.tabs.single { it.id == state.currentTabId }.url)
        assertEquals("Heat pump research", state.groups.single().title)
        assertEquals(listOf(HEAT, SIZING), state.tabs.filter { it.groupId != null }.map { it.url })
        assertTrue(checkNotNull(state.tabs.single { it.url == HEAT }.chatId) > 0)
        assertNull(state.tabs.single { it.url == NOTES }.chatId)
    }

    companion object {
        private const val HEAT = "https://daylightjournal.com/energy/heat-pumps-cold"
        private const val SIZING = "https://heatcalc.example/sizing"
        private const val NOTES = "https://kotlinlang.org/docs/releases.html"
        private const val SECRET = "https://secret.example/inbox"

        @field:ClassRule
        @JvmField
        val folder = TemporaryFolder()
    }
}
