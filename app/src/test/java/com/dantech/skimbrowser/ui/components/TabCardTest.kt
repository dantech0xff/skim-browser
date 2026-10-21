package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var badgeColor = Color.Unspecified

    @Test
    fun tappingTheCardOpensTheTab() {
        setCard()

        composeRule.onNode(hasText(TITLE) and hasClickAction()).performClick()

        assertEquals(listOf("open"), calls)
    }

    @Test
    fun tappingCloseClosesTheTabOnly() {
        setCard()

        composeRule.onNodeWithContentDescription(CLOSE_TAB).performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun theCardIsAButtonNamedByItsTitle() {
        setCard()

        composeRule.onNode(hasText(TITLE) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // The favicon letter is decoration beside the title.
        composeRule.onNodeWithText(LETTER).assertDoesNotExist()
    }

    @Test
    fun theCurrentTabReadsAsSelected() {
        setCard(isCurrent = true)

        composeRule.onNode(hasText(TITLE) and hasClickAction()).assertIsSelected()
    }

    @Test
    fun anotherTabDoesNotReadAsSelected() {
        setCard(isCurrent = false)

        composeRule.onNode(hasText(TITLE) and hasClickAction()).assertIsNotSelected()
    }

    @Test
    fun aTabWithASkimChatShowsTheSunnyBadge() {
        setCard(hasSkimChat = true)

        assertTrue(cardImage().hasPixel(badgeColor))
    }

    @Test
    fun aTabWithoutASkimChatShowsNoBadge() {
        setCard(hasSkimChat = false)

        assertFalse(cardImage().hasPixel(badgeColor))
    }

    @Test
    fun anIncognitoTabNeverShowsTheBadge() {
        setCard(hasSkimChat = true, incognito = true)

        assertFalse(cardImage().hasPixel(badgeColor))
    }

    @Test
    fun theCardIs144HighWithA44TitleRow() {
        setCard()

        composeRule.onNodeWithTag(CARD).assertHeightIsEqualTo(144.dp)
        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText(TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(22f, ((title.top + title.bottom) / 2 - card.top).value, 0.5f)
    }

    @Test
    fun closeKeepsA48TargetInsideTheCard() {
        setCard()

        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val touch = with(composeRule.density) {
            val bounds = composeRule.onNodeWithContentDescription(CLOSE_TAB).fetchSemanticsNode().touchBoundsInRoot
            listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).map { it.toDp().value }
        }
        val (left, top, right, bottom) = touch
        assertEquals(48f, right - left, 0.5f)
        assertEquals(48f, bottom - top, 0.5f)
        // Flush with the top end corner, so no touch outside the card reaches it.
        assertEquals(card.right.value, right, 0.5f)
        assertEquals(card.top.value, top, 0.5f)
        // The title stops where that target starts.
        val title = composeRule.onNodeWithText(TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(title.right.value <= left + 0.5f)
    }

    private fun setCard(isCurrent: Boolean = false, hasSkimChat: Boolean = false, incognito: Boolean = false) {
        composeRule.setSkimContent(incognito = incognito) {
            badgeColor = MaterialTheme.colorScheme.tertiaryContainer
            TabCard(
                title = TITLE,
                letter = LETTER,
                preview = null,
                isCurrent = isCurrent,
                hasSkimChat = hasSkimChat,
                incognito = incognito,
                onClick = { calls += "open" },
                onClose = { calls += "close" },
                modifier = Modifier.width(184.dp).testTag(CARD),
            )
        }
    }

    private fun cardImage(): ImageBitmap = composeRule.onNodeWithTag(CARD).captureToImage()

    private companion object {
        const val CARD = "card"
        const val TITLE = "Why heat pumps are winning in cold climates"
        const val LETTER = "D"
        const val CLOSE_TAB = "Close tab"
    }
}
