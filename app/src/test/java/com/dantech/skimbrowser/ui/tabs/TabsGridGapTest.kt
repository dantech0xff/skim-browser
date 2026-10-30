package com.dantech.skimbrowser.ui.tabs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.TabGroup
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/** The grid keeps 12 dp between cards, 8 dp under a group header, and 16 dp between sections. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabsGridGapTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerCardAndSectionGapsMatchTheBoard() {
        composeRule.setSkimContent {
            TabsScreen(state = twoSections(), searchState = null, actions = idle())
        }
        composeRule.waitForIdle()

        val headers = composeRule.onAllNodesWithTag("tab-section-header").fetchSemanticsNodes()
        val cards = composeRule.onAllNodesWithTag("tab-card").fetchSemanticsNodes()
        val firstHeader = headers[0].boundsInRoot
        val firstRow = cards[0].boundsInRoot
        val secondRow = cards[2].boundsInRoot
        val secondHeader = headers[1].boundsInRoot
        val laterCard = cards[3].boundsInRoot

        assertEquals(8, dp(firstRow.top - firstHeader.bottom))
        assertEquals(12, dp(secondRow.top - firstRow.bottom))
        assertEquals(16, dp(secondHeader.top - secondRow.bottom))
        assertEquals(8, dp(laterCard.top - secondHeader.bottom))
    }

    private fun dp(px: Float): Int = (px / composeRule.density.density).roundToInt()

    private fun twoSections() = TabsUiState(
        sections = persistentListOf(
            TabSection(
                TabGroup(1, "Heat", 0),
                persistentListOf(
                    TabCardState("a", "Alpha", "A", isCurrent = true, hasSkimChat = false),
                    TabCardState("b", "Beta", "B", isCurrent = false, hasSkimChat = false),
                    TabCardState("d", "Delta", "D", isCurrent = false, hasSkimChat = false),
                ),
                showHeader = true,
            ),
            TabSection(
                null,
                persistentListOf(TabCardState("c", "Gamma", "G", isCurrent = false, hasSkimChat = false)),
                showHeader = true,
            ),
        ),
    )

    private fun idle() = TabsActions(
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
        onNewIncognitoTab = {},
        onCloseAll = {},
        onNewTab = {},
    )
}
