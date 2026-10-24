package com.dantech.skimbrowser.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.GroupingBannerState
import com.dantech.skimbrowser.browser.TabGroup
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tabs (09), Incognito tabs (09b), and the group menu and rename dialog those screens open. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabs() {
        show(board())
        captureBoard("09-tabs")
        composeRule.assertA11y()
    }

    @Test
    fun incognitoTabs() {
        composeRule.setSkimContent(incognito = true) {
            TabsScreen(state = incognitoBoard(), searchState = null, actions = idleActions(incognito = true))
        }
        composeRule.waitForIdle()
        captureBoard("09b-incognito-tabs")
        composeRule.assertA11y()
    }

    @Test
    fun groupMenu() {
        show(board())
        composeRule.onAllNodesWithContentDescription("Group options")[0].performClick()
        composeRule.waitForIdle()
        captureExtra("09-tabs--menu")
        composeRule.assertA11y()
    }

    @Test
    fun renameDialog() {
        composeRule.setSkimContent {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow))
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                RenameGroupDialog(initialName = "Heat pump research", onSave = {}, onDismiss = {})
            }
        }
        composeRule.waitForIdle()
        captureExtra("dialog-rename-group-15")
        composeRule.assertA11y()
    }

    @Test
    fun search() {
        show(
            board().copy(
                query = "osaka",
                sections = persistentListOf(board().sections[1]),
                grouping = null,
            ),
            search = TextFieldState("osaka"),
        )
        captureExtra("09-tabs--search")
        composeRule.assertA11y()
    }

    @Test
    fun empty() {
        show(TabsUiState(count = 0))
        captureExtra("09-tabs--empty")
        composeRule.assertA11y()
    }

    @Test
    fun ungrouped() {
        show(
            TabsUiState(
                count = 2,
                sections = persistentListOf(
                    TabSection(
                        group = null,
                        showHeader = false,
                        tabs = persistentListOf(
                            card("a", "Why heat pumps are winning in cold climates", "D", current = true),
                            card("b", "Kotlin 2.2 release notes", "K"),
                        ),
                    ),
                ),
            ),
        )
        captureExtra("09-tabs--ungrouped")
        composeRule.assertA11y()
    }

    private fun show(state: TabsUiState, search: TextFieldState? = null) {
        composeRule.setSkimContent {
            TabsScreen(state = state, searchState = search, actions = idleActions())
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureExtra(name: String) {
        captureScreenRoboImage("src/test/screenshots/boards/$name.png")
    }

    private fun board(): TabsUiState {
        val heat = TabGroup(id = 1, title = "Heat pump research", position = 0)
        val osaka = TabGroup(id = 2, title = "Osaka trip", position = 1)
        return TabsUiState(
            count = 7,
            grouping = GroupingBannerState(undoEnabled = true),
            sections = persistentListOf(
                TabSection(
                    heat,
                    persistentListOf(
                        card("1", "Why heat pumps are winning in cold climates", "D", current = true, chat = true),
                        card("2", "Heat pump sizing calculator", "H"),
                        card("3", "Installer reviews near me", "R"),
                        card("4", "Heat pump rebates 2026", "E"),
                    ),
                    showHeader = true,
                ),
                TabSection(
                    osaka,
                    persistentListOf(
                        card("5", "Osaka night buses: a complete guide", "T"),
                        card("6", "Namba hotels, 2 nights", "N"),
                    ),
                    showHeader = true,
                ),
                TabSection(
                    null,
                    persistentListOf(card("7", "Kotlin 2.2 release notes", "K")),
                    showHeader = true,
                ),
            ),
        )
    }

    private fun incognitoBoard() = TabsUiState(
        incognito = true,
        count = 2,
        sections = persistentListOf(
            TabSection(
                group = null,
                showHeader = false,
                tabs = persistentListOf(
                    card("1", "Anniversary gift ideas", "G", current = true),
                    card("2", "Flights to Lisbon in May", "F"),
                ),
            ),
        ),
    )

    private fun card(
        id: String,
        title: String,
        letter: String,
        current: Boolean = false,
        chat: Boolean = false,
    ) = TabCardState(id, title, letter, isCurrent = current, hasSkimChat = chat)

    private fun idleActions(incognito: Boolean = false) = TabsActions(
        onSearch = {},
        onCloseSearch = {},
        onSelectMode = {},
        onOpen = {},
        onClose = {},
        onMove = { _, _ -> },
        onRename = {},
        onUngroup = {},
        onCloseGroup = {},
        onUndoGrouping = {},
        onAskSkim = {},
        onNewIncognitoTab = if (incognito) null else ({}),
        onCloseAll = {},
        onNewTab = {},
    )
}
