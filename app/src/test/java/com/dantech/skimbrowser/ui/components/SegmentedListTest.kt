package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SegmentedListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0
    private var longClicks = 0

    @Test
    fun aSingleItemIsRoundAllRound() {
        assertCorners(segmentedShapes(index = 0, count = 1).shape, top = 20f, bottom = 20f)
    }

    @Test
    fun twoItemsMeetWithSmallCorners() {
        assertCorners(segmentedShapes(index = 0, count = 2).shape, top = 20f, bottom = 4f)
        assertCorners(segmentedShapes(index = 1, count = 2).shape, top = 4f, bottom = 20f)
    }

    @Test
    fun middleItemsHaveSmallCornersAllRound() {
        assertCorners(segmentedShapes(index = 0, count = 4).shape, top = 20f, bottom = 4f)
        assertCorners(segmentedShapes(index = 1, count = 4).shape, top = 4f, bottom = 4f)
        assertCorners(segmentedShapes(index = 2, count = 4).shape, top = 4f, bottom = 4f)
        assertCorners(segmentedShapes(index = 3, count = 4).shape, top = 4f, bottom = 20f)
    }

    @Test
    fun onlyPressingChangesTheShape() {
        val shapes: ListItemShapes = segmentedShapes(index = 1, count = 3)

        assertCorners(shapes.pressedShape, top = 20f, bottom = 20f)
        listOf(shapes.selectedShape, shapes.focusedShape, shapes.hoveredShape, shapes.draggedShape).forEach {
            assertSame(shapes.shape, it)
        }
    }

    @Test
    fun aPlaceOutsideTheGroupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { segmentedShapes(index = 0, count = 0) }
        assertThrows(IllegalArgumentException::class.java) { segmentedShapes(index = 3, count = 3) }
        assertThrows(IllegalArgumentException::class.java) { segmentedShapes(index = -1, count = 3) }
    }

    @Test
    fun aOneLineItemWithAnIconButtonStays56High() {
        composeRule.setSkimContent {
            SkimListItem(
                headline = LONG_HEADLINE,
                index = 0,
                count = 1,
                onClick = {},
                modifier = Modifier.testTag(ITEM),
                leading = {
                    val colors = MaterialTheme.colorScheme
                    IconTile(R.drawable.ic_search, colors.surfaceContainer, colors.onSurfaceVariant)
                },
                trailing = { RemoveButton() },
                trailingIsIconButton = true,
                singleLine = true,
            )
        }

        composeRule.onNodeWithTag(ITEM).assertHeightIsEqualTo(56.dp)
        val item = composeRule.onNodeWithTag(ITEM).getUnclippedBoundsInRoot()
        // The drawn 40 dp button, in the middle of its 48 dp target: that target sits 4 dp from the
        // end and spans the item's vertical padding, as on 04b and 13.
        val button = composeRule.onNodeWithContentDescription(REMOVE).getUnclippedBoundsInRoot()
        assertEquals(28f, (item.right - (button.left + button.right) / 2).value, 0.5f)
        assertEquals(28f, ((button.top + button.bottom) / 2 - item.top).value, 0.5f)
        // The headline runs up to the target, with no gap before it.
        val headline = composeRule.onNodeWithText(LONG_HEADLINE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(52f, (item.right - headline.right).value, 0.5f)
    }

    @Test
    fun aTwoLineItemIs72High() {
        composeRule.setSkimContent {
            SkimListItem(
                headline = HEADLINE,
                supporting = SUPPORTING,
                index = 0,
                count = 1,
                onClick = {},
                modifier = Modifier.testTag(ITEM),
                leading = { FaviconLetterTile("D") },
                trailing = { RemoveButton() },
                trailingIsIconButton = true,
                singleLine = true,
            )
        }

        composeRule.onNodeWithTag(ITEM).assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun aValueKeeps16FromTheEnd() {
        composeRule.setSkimContent {
            SkimListItem(
                headline = HEADLINE,
                index = 0,
                count = 1,
                onClick = null,
                modifier = Modifier.testTag(ITEM),
                trailing = { Text("1.0.0 (100)", Modifier.testTag(VALUE)) },
            )
        }

        val item = composeRule.onNodeWithTag(ITEM).getUnclippedBoundsInRoot()
        val value = composeRule.onNodeWithTag(VALUE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(16f, (item.right - value.right).value, 0.5f)
    }

    @Test
    fun aStaticListItemIs48HighAndCannotBeTapped() {
        composeRule.setSkimContent(incognito = true) {
            StaticListItem(R.drawable.ic_history, HEADLINE, index = 0, count = 3, modifier = Modifier.testTag(ITEM))
        }

        composeRule.onNodeWithTag(ITEM)
            .assertHeightIsEqualTo(48.dp)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun anItemWithoutAClickCannotBeTapped() {
        composeRule.setSkimContent {
            SkimListItem(headline = HEADLINE, index = 0, count = 1, onClick = null, modifier = Modifier.testTag(ITEM))
        }

        composeRule.onNodeWithTag(ITEM).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun tappingAndLongPressingReachTheCaller() {
        composeRule.setSkimContent {
            SkimListItem(
                headline = HEADLINE,
                index = 0,
                count = 1,
                onClick = { clicks++ },
                onLongClick = { longClicks++ },
                modifier = Modifier.testTag(ITEM),
            )
        }

        composeRule.onNodeWithTag(ITEM).performClick()
        composeRule.onNodeWithTag(ITEM).performTouchInput { longClick() }

        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }

    @Composable
    private fun RemoveButton() {
        IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_close), REMOVE) }
    }

    private fun assertCorners(shape: Shape, top: Float, bottom: Float) {
        val rounded = shape as RoundedCornerShape
        val size = Size(400f, 72f)
        val density = Density(1f)
        assertEquals(top, rounded.topStart.toPx(size, density), 0.01f)
        assertEquals(top, rounded.topEnd.toPx(size, density), 0.01f)
        assertEquals(bottom, rounded.bottomStart.toPx(size, density), 0.01f)
        assertEquals(bottom, rounded.bottomEnd.toPx(size, density), 0.01f)
    }

    private companion object {
        const val ITEM = "item"
        const val VALUE = "value"
        const val HEADLINE = "Clear browsing data"
        const val LONG_HEADLINE = "Why heat pumps are winning in cold climates, and what that means for your home"
        const val SUPPORTING = "daylightjournal.com"
        const val REMOVE = "Remove from history"
    }
}
