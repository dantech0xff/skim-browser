package com.dantech.skimbrowser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SkimSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()

    @Test
    fun theWholeRowIsOneSwitchLabelledByItsText() {
        setRow(checked = true)

        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithTag(ROW)
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasText(HEADLINE))
            .assert(hasText(SUPPORTING))
    }

    @Test
    fun tappingTheTextTogglesTheSwitch() {
        setRow(checked = true)

        composeRule.onNodeWithTag(ROW).performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun tappingTheSwitchItselfTogglesTheRowOnce() {
        setRow(checked = false)

        // The switch is drawn 16 dp from the end of the row.
        composeRule.onNodeWithTag(ROW).performTouchInput { click(centerRight.copy(x = centerRight.x - 40.dp.toPx())) }

        assertEquals(listOf(true), changes)
    }

    @Test
    fun theRowFollowsTheCallersState() {
        composeRule.setSkimContent {
            var checked by remember { mutableStateOf(true) }
            SwitchRow(checked = checked, onCheckedChange = { checked = it })
        }

        composeRule.onNodeWithTag(ROW).performClick()

        composeRule.onNodeWithTag(ROW).assertIsOff()
    }

    @Test
    fun aSwitchOnItsOwnIsASwitch() {
        composeRule.setSkimContent {
            var checked by remember { mutableStateOf(false) }
            SkimSwitch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag(SWITCH))
        }

        composeRule.onNodeWithTag(SWITCH)
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()
            .assertIsOn()
    }

    private fun setRow(checked: Boolean) {
        composeRule.setSkimContent {
            SwitchRow(checked = checked, onCheckedChange = { changes += it })
        }
    }

    @Composable
    private fun SwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        SkimListItem(
            headline = HEADLINE,
            supporting = SUPPORTING,
            index = 0,
            count = 1,
            onClick = null,
            modifier = Modifier.testTag(ROW).toggleableRow(checked, onCheckedChange),
            trailing = { SkimSwitch(checked, onCheckedChange = null) },
        )
    }

    private companion object {
        const val ROW = "row"
        const val SWITCH = "switch"
        const val HEADLINE = "Keep chat history on this device"
        const val SUPPORTING = "Never synced. Delete it anytime below."
    }
}
