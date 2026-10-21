package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SkimMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var expanded by mutableStateOf(true)

    @Test
    fun choosingAnItemRunsItsActionThenCloses() {
        setMenu()

        composeRule.onNodeWithText(UNGROUP).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(UNGROUP, "dismiss"), calls)
        assertFalse(expanded)
        composeRule.onNodeWithText(UNGROUP).assertDoesNotExist()
    }

    @Test
    fun backClosesTheMenuWithoutAnAction() {
        setMenu()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(listOf("dismiss"), calls)
        composeRule.onNodeWithText(RENAME).assertDoesNotExist()
    }

    @Test
    fun aDestructiveItemSitsInAGroupOfItsOwn() {
        setMenu()

        val rename = composeRule.onNodeWithText(RENAME).getUnclippedBoundsInRoot()
        val ungroup = composeRule.onNodeWithText(UNGROUP).getUnclippedBoundsInRoot()
        val close = composeRule.onNodeWithText(CLOSE_TABS).getUnclippedBoundsInRoot()
        // Items of a group touch; the next group starts after 4 dp of padding, the 2 dp gap and
        // 4 dp of padding again.
        assertEquals(0f, (ungroup.top - rename.bottom).value, 0.5f)
        assertEquals(10f, (close.top - ungroup.bottom).value, 0.5f)
    }

    @Test
    fun itemsAre48HighAnd212WideInA220Menu() {
        setMenu()

        listOf(RENAME, UNGROUP, CLOSE_TABS).forEach { label ->
            composeRule.onNodeWithText(label).assertHeightIsEqualTo(48.dp)
            val item = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
            // 4 dp of group padding on each side.
            assertEquals(212f, (item.right - item.left).value, 0.5f)
        }
    }

    @Test
    fun theMenuIsAnnouncedAsWhatItIsFor() {
        setMenu()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, MENU_LABEL)).assertExists()
    }

    private fun setMenu() {
        composeRule.setSkimContent {
            Box {
                Box(Modifier.size(48.dp))
                SkimMenu(
                    expanded = expanded,
                    onDismiss = {
                        calls += "dismiss"
                        expanded = false
                    },
                    groups = listOf(
                        listOf(
                            MenuEntry(RENAME, R.drawable.ic_drive_file_rename_outline) { calls += RENAME },
                            MenuEntry(UNGROUP, R.drawable.ic_layers_clear) { calls += UNGROUP },
                        ),
                        listOf(MenuEntry(CLOSE_TABS, R.drawable.ic_close) { calls += CLOSE_TABS }),
                    ),
                    anchorContentDescription = MENU_LABEL,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val RENAME = "Rename group"
        const val UNGROUP = "Ungroup"
        const val CLOSE_TABS = "Close 4 tabs"
        const val MENU_LABEL = "Heat pump research group"
    }
}
