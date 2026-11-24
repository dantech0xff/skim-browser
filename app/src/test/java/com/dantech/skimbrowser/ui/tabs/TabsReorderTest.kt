package com.dantech.skimbrowser.ui.tabs

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.tab.TabGroup
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * While a tab card is dragged over its own group, the other cards make room for it; the drop asks
 * for that order once, and anything else shows the real order again.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class TabsReorderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(board())

    @After
    fun restoreAnimations() {
        animatorScale(1f)
    }

    @Test
    fun aNeighbourOfTheSameGroupMovesIntoTheSlotTheDraggedCardLeftWhileTheFingerIsDown() {
        show()
        val a = bounds(A)
        val b = bounds(B)

        pickUp(A)
        moveTo(b.center())

        assertSameSlot(a, bounds(B))
        assertSameCentre("the dragged card is under the finger", b.center(), bounds(A).center())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun theDropAsksForThePreviewedIndexOnceAndTheCardsKeepTheNewOrder() {
        show(applyMoves = true)
        val a = bounds(A)
        val b = bounds(B)
        val c = bounds(C)

        pickUp(A)
        moveTo(c.center())
        assertSameSlot("previewed before the drop", b, bounds(C))
        release()

        assertEquals(listOf("move:a:2"), calls)
        assertEquals(listOf("b", "c", "a"), state.sections[0].tabs.map { it.id })
        assertSameSlot(c, bounds(A))
        assertSameSlot(a, bounds(B))
        assertSameSlot(b, bounds(C))
    }

    @Test
    fun aMoveTheViewModelDoesNotApplyLeavesTheCardsInTheRealOrder() {
        show(applyMoves = false)
        val a = bounds(A)
        val b = bounds(B)

        pickUp(A)
        moveTo(b.center())
        assertSameSlot("previewed before the drop", a, bounds(B))
        release()

        assertEquals(listOf("move:a:1"), calls)
        assertSameSlot(a, bounds(A))
        assertSameSlot(b, bounds(B))
    }

    @Test
    fun overACardOfAnotherGroupTheOriginalOrderShowsAndTheDropCallsNothing() {
        show(applyMoves = true)
        val a = bounds(A)
        val b = bounds(B)
        val d = bounds(D)

        pickUp(A)
        moveTo(b.center())
        assertSameSlot("previewed over its own group", a, bounds(B))
        moveTo(d.center())

        assertSameSlot(b, bounds(B))
        assertSameSlot(d, bounds(D))
        assertSameCentre("the dragged card is under the finger", d.center(), bounds(A).center())

        release()
        assertTrue(calls.isEmpty())
        assertSameSlot(a, bounds(A))
    }

    @Test
    fun aCancelledDragRestoresTheOrderAndCallsNothing() {
        show(applyMoves = true)
        val a = bounds(A)
        val b = bounds(B)

        pickUp(A)
        moveTo(b.center())
        assertSameSlot("previewed before the cancel", a, bounds(B))
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.waitForIdle()

        assertTrue(calls.isEmpty())
        assertSameSlot(a, bounds(A))
        assertSameSlot(b, bounds(B))
    }

    @Test
    fun withTheTopOfTheGroupScrolledAwayTheDropAsksForTheIndexInTheWholeGroup() {
        state = TabsUiState(
            count = 16,
            sections = persistentListOf(
                TabSection(
                    group = null,
                    showHeader = false,
                    tabs = List(16) { card("t$it", "Tab number $it") }.toPersistentList(),
                ),
            ),
        )
        show()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
        composeRule.waitForIdle()
        assertTrue(
            "the first cards are scrolled away",
            composeRule.onAllNodes(hasText("Tab number 0")).fetchSemanticsNodes().isEmpty(),
        )
        val last = bounds("Tab number 15")

        pickUp("Tab number 14")
        moveTo(last.center())
        release()

        assertEquals("not its index among the cards on screen", listOf("move:t14:15"), calls)
    }

    @Test
    fun withRemoveAnimationsTheNeighbourIsInItsNewSlotAtOnce() {
        animatorScale(0f)
        show()
        val a = bounds(A)
        val b = bounds(B)
        composeRule.mainClock.autoAdvance = false

        val timeout = composeRule.onRoot().fetchSemanticsNode().layoutInfo.viewConfiguration.longPressTimeoutMillis
        composeRule.onRoot().performTouchInput { down(a.center()) }
        composeRule.mainClock.advanceTimeBy(timeout + 16)
        composeRule.onRoot().performTouchInput { moveTo(a.center() + Offset(0f, 4.dp.toPx())) }
        advanceFrames()
        composeRule.onRoot().performTouchInput { moveTo(b.center()) }
        advanceFrames()

        assertSameSlot(a, bounds(B))
        composeRule.mainClock.autoAdvance = true
    }

    // Helpers

    private fun show(applyMoves: Boolean = false) {
        composeRule.setSkimContent {
            TabsScreen(
                state = state,
                searchState = null,
                actions = actions { id, index ->
                    calls += "move:$id:$index"
                    if (applyMoves) state = moved(state, id, index)
                },
            )
        }
        composeRule.waitForIdle()
    }

    private fun card(title: String): SemanticsNodeInteraction = composeRule.onNode(hasText(title) and hasClickAction())

    /** [title]'s card, in pixels of the root. */
    private fun bounds(title: String): PxRect {
        val rect: DpRect = card(title).getBoundsInRoot()
        val density = composeRule.density.density
        return PxRect(
            rect.left.value * density,
            rect.top.value * density,
            rect.right.value * density,
            rect.bottom.value * density,
        )
    }

    /** Holds [title]'s card past a long press and starts the drag with a small move. */
    private fun pickUp(title: String) {
        val start = bounds(title).center()
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(start + Offset(0f, 4.dp.toPx()))
        }
        composeRule.waitForIdle()
    }

    private fun moveTo(position: Offset) {
        composeRule.onRoot().performTouchInput { moveTo(position) }
        composeRule.waitForIdle()
    }

    private fun release() {
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun advanceFrames() {
        composeRule.waitForIdle()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun animatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun assertSameSlot(expected: PxRect, actual: PxRect) = assertSameSlot("", expected, actual)

    private fun assertSameSlot(message: String, expected: PxRect, actual: PxRect) {
        val text = "$message: expected $expected, was $actual"
        assertEquals(text, expected.left, actual.left, TOLERANCE_PX)
        assertEquals(text, expected.top, actual.top, TOLERANCE_PX)
        assertEquals(text, expected.right, actual.right, TOLERANCE_PX)
        assertEquals(text, expected.bottom, actual.bottom, TOLERANCE_PX)
    }

    private fun assertSameCentre(message: String, expected: Offset, actual: Offset) {
        assertEquals("$message: x", expected.x, actual.x, TOLERANCE_PX)
        assertEquals("$message: y", expected.y, actual.y, TOLERANCE_PX)
    }

    /** [id] moved to [toIndex] inside its section, as the ViewModel's layout would put it. */
    private fun moved(state: TabsUiState, id: String, toIndex: Int): TabsUiState {
        val sections = state.sections.map { section ->
            val from = section.tabs.indexOfFirst { it.id == id }
            if (from < 0) return@map section
            val tabs = section.tabs.toMutableList()
            tabs.add(toIndex.coerceIn(0, tabs.lastIndex), tabs.removeAt(from))
            section.copy(tabs = tabs.toImmutableList())
        }
        return state.copy(sections = sections.toPersistentList())
    }

    private fun board() = TabsUiState(
        count = 5,
        sections = persistentListOf(
            TabSection(
                group = TabGroup(id = 1, title = "Heat pump research", position = 0),
                showHeader = true,
                tabs = persistentListOf(card("a", A), card("b", B), card("c", C)),
            ),
            TabSection(
                group = TabGroup(id = 2, title = "Osaka trip", position = 1),
                showHeader = true,
                tabs = persistentListOf(card("d", D), card("e", E)),
            ),
        ),
    )

    private fun card(id: String, title: String) =
        TabCardState(id, title, title.take(1), isCurrent = id == "a", hasSkimChat = false)

    private fun actions(onMove: (String, Int) -> Unit) = TabsActions(
        onSearch = {},
        onCloseSearch = {},
        onSelectMode = {},
        onOpen = { calls += "open:$it" },
        onClose = { calls += "close:$it" },
        onMove = onMove,
        onRename = {},
        onUngroup = {},
        onCloseGroup = {},
        onUndoGrouping = {},
        onAskSkim = {},
        onNewIncognitoTab = {},
        onCloseAll = {},
        onNewTab = {},
    )

    /** A rectangle in root pixels. */
    private data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun center() = Offset((left + right) / 2, (top + bottom) / 2)
    }

    private companion object {
        const val A = "Why heat pumps are winning in cold climates"
        const val B = "Heat pump sizing guide"
        const val C = "Cold climate heat pump list"
        const val D = "Osaka food guide"
        const val E = "Kyoto day trip"

        /** A few frames: long enough for a snap, far shorter than the expressive springs. */
        const val FRAMES = 4

        /** One and a half pixels, for rounding of the drag and the layout. */
        const val TOLERANCE_PX = 1.5f
    }
}
