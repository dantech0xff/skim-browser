package com.dantech.skimbrowser.ui.ai

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How Ask Skim's content comes in: the suggested questions one after another, a question and its
 * answer rising in, and a streaming answer that keeps the end of the chat in view.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AskSkimMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var page by mutableStateOf(AskPageUiState())
    private var tabs by mutableStateOf(AskTabsUiState())

    // Suggested questions

    @Test
    fun theSuggestedQuestionsArriveOneAfterAnother() {
        composeRule.mainClock.autoAdvance = false
        showPage(summarized())
        advanceFrames()
        val coming = CHIPS.map(::top)
        settle()
        val rest = CHIPS.map(::top)

        val first = coming[0] - rest[0]
        val last = coming[2] - rest[2]
        assertTrue("the first is rising: $first", first > 0.dp && first < EntranceRiseDp)
        assertTrue("the last starts later: $last", last > first)
    }

    @Test
    fun theSuggestedQuestionsDoNotComeInAgain() {
        showPage(summarized())
        val rest = CHIPS.map(::top)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { page = page.copy(title = "Heat pumps, updated") }
        advanceFrames()
        assertEquals("on recomposition", rest, CHIPS.map(::top))

        composeRule.runOnIdle { page = page.copy(showChips = false) }
        advanceFrames()
        composeRule.runOnIdle { page = page.copy(showChips = true) }
        advanceFrames()
        assertEquals("when they show again", rest, CHIPS.map(::top))
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun withRemoveAnimationsTheSuggestedQuestionsAreInPlaceAtOnce() {
        removeAnimations()
        composeRule.mainClock.autoAdvance = false
        showPage(summarized())
        advanceFrames()
        val shown = CHIPS.map(::top)
        settle()

        assertEquals(CHIPS.map(::top), shown)
    }

    // Questions and answers

    @Test
    fun aQuestionAndItsAnswerRiseInWhenTheyAreAddedButNotTheTurnsTheSheetOpenedWith() {
        composeRule.mainClock.autoAdvance = false
        showPage(summarized(turns = listOf(QaTurn(OLD_QUESTION, OLD_ANSWER, persistentListOf(), stopped = false))))
        advanceFrames()
        val opened = top(OLD_QUESTION)
        settle()
        assertEquals("a turn the sheet opened with", top(OLD_QUESTION), opened)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { page = page.ask(NEW_QUESTION) }
        advanceFrames()
        val asked = top(NEW_QUESTION)
        settle()
        assertTrue("the question rises in", asked > top(NEW_QUESTION))
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { page = page.answer(NEW_ANSWER) }
        advanceFrames()
        val answered = top(NEW_ANSWER)
        settle()
        assertTrue("the answer rises in", answered > top(NEW_ANSWER))
    }

    @Test
    fun withRemoveAnimationsAnAddedQuestionIsInPlaceAtOnce() {
        removeAnimations()
        showPage(summarized())
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { page = page.ask(NEW_QUESTION) }
        advanceFrames()
        val asked = top(NEW_QUESTION)
        settle()

        assertEquals(top(NEW_QUESTION), asked)
    }

    @Test
    fun aFollowUpAndItsAnswerRiseInAcrossTabs() {
        showTabs(TabsReady)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            tabs = tabs.copy(turns = persistentListOf(TabsAnswer(NEW_QUESTION, null, stopped = false)), generating = true)
        }
        advanceFrames()
        val asked = top(NEW_QUESTION)
        settle()

        assertTrue("the follow-up rises in", asked > top(NEW_QUESTION))
    }

    // Streaming and the scroll position

    @Test
    fun atTheEndAStreamingAnswerStaysInView() {
        showPage(streaming(OLD_ANSWER), bodyHeight = SHORT_BODY)
        scrollBy(LARGE)
        val before = range()
        assertTrue("the chat is longer than the sheet", before.maxValue > 0f)
        assertEquals("at the end", before.maxValue, before.value, 0.5f)

        composeRule.runOnIdle { page = page.answer(LONG_ANSWER) }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat grew", after.maxValue > before.maxValue)
        assertEquals("still at the end", after.maxValue, after.value, 0.5f)
    }

    @Test
    fun scrolledUpAStreamingAnswerDoesNotMoveThePosition() {
        showPage(streaming(OLD_ANSWER), bodyHeight = SHORT_BODY)
        scrollBy(LARGE)
        scrollBy(-UP)
        val before = range()
        assertTrue(before.value < before.maxValue)

        composeRule.runOnIdle { page = page.answer(LONG_ANSWER) }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat grew", after.maxValue > before.maxValue)
        assertEquals("the position did not move", before.value, after.value, 0.5f)
    }

    @Test
    fun atTheEndAStreamingAnswerAcrossTabsStaysInView() {
        showTabs(TabsReady.copy(turns = persistentListOf(TabsAnswer(NEW_QUESTION, LONG_ANSWER, stopped = false)), generating = true), bodyHeight = SHORT_BODY)
        scrollBy(LARGE)
        val before = range()
        assertTrue("the chat is longer than the sheet", before.maxValue > 0f)
        assertEquals("at the end", before.maxValue, before.value, 0.5f)

        composeRule.runOnIdle {
            tabs = tabs.copy(turns = persistentListOf(TabsAnswer(NEW_QUESTION, "$LONG_ANSWER $LONG_ANSWER", stopped = false)))
        }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat grew", after.maxValue > before.maxValue)
        assertEquals("still at the end", after.maxValue, after.value, 0.5f)
    }

    @Test
    fun aSummaryThatGrowsPastTheSheetDoesNotMoveAReaderAtTheTop() {
        showPage(summarized())
        assertEquals("the chat fits", 0f, range().maxValue, 0.5f)

        composeRule.runOnIdle { page = page.copy(summary = LongSummary) }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat grew past the sheet", after.maxValue > 0f)
        assertEquals("still at the top", 0f, after.value, 0.5f)
    }

    @Test
    fun aQuestionAskedWhileTheChatFitsIsFollowedWhenItsAnswerGrowsPastTheSheet() {
        showPage(summarized())
        assertEquals("the chat fits", 0f, range().maxValue, 0.5f)

        composeRule.runOnIdle { page = page.ask(NEW_QUESTION) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { page = page.answer(List(8) { LONG_ANSWER }.joinToString(" ")) }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat grew past the sheet", after.maxValue > 0f)
        assertEquals("at the end", after.maxValue, after.value, 0.5f)
    }

    @Test
    fun aQuestionAskedAfterScrollingUpBringsTheChatBackToItsEnd() {
        showPage(streaming(OLD_ANSWER).copy(generating = false), bodyHeight = SHORT_BODY)
        scrollBy(LARGE)
        scrollBy(-UP)
        assertTrue(range().value < range().maxValue)

        composeRule.runOnIdle { page = page.ask("And in a flat?") }
        composeRule.waitForIdle()

        val after = range()
        assertEquals("at the end", after.maxValue, after.value, 0.5f)
    }

    @Test
    fun aChatThatLoadsAfterTheSheetOpensStaysAtItsTop() {
        // The sheet is composed with the empty state first; the saved chat arrives after.
        showPage(AskPageUiState(), bodyHeight = SHORT_BODY)

        composeRule.runOnIdle { page = streaming(LONG_ANSWER).copy(generating = false) }
        composeRule.waitForIdle()

        val after = range()
        assertTrue("the chat is longer than the sheet", after.maxValue > 0f)
        assertEquals("at the top", 0f, after.value, 0.5f)
    }

    // Helpers

    private fun showPage(state: AskPageUiState, bodyHeight: Dp = TALL_BODY) {
        page = state
        composeRule.setSkimContent {
            AskPageSheet(state = page, actions = PageActions, snackbar = remember { SnackbarHostState() }, bodyHeight = bodyHeight)
        }
        composeRule.waitForIdle()
    }

    private fun showTabs(state: AskTabsUiState, bodyHeight: Dp = TALL_BODY) {
        tabs = state
        composeRule.setSkimContent {
            AskTabsSheet(state = tabs, actions = TabsActions, snackbar = remember { SnackbarHostState() }, bodyHeight = bodyHeight)
        }
        composeRule.waitForIdle()
    }

    private fun top(text: String): Dp = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot().top

    private fun scroller() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

    /** The scroll position and its end now. The semantics range reads them live, so they are copied. */
    private fun range(): Position = scroller().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        .let { Position(it.value(), it.maxValue()) }

    private data class Position(val value: Float, val maxValue: Float)

    private fun scrollBy(pixels: Float) {
        scroller().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        composeRule.waitForIdle()
    }

    private fun advanceFrames() {
        composeRule.waitForIdle()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun removeAnimations() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun AskPageUiState.ask(question: String) = copy(
        turns = (turns + QaTurn(question, null, persistentListOf(), stopped = false)).toImmutableList(),
        showChips = false,
        generating = true,
        waitingForAnswer = true,
    )

    private fun AskPageUiState.answer(text: String) = copy(
        turns = (turns.dropLast(1) + turns.last().copy(answer = text)).toImmutableList(),
        waitingForAnswer = false,
    )

    private companion object {
        /** Three frames: the first suggestion is on its way, the last is still held back. */
        const val FRAMES = 3
        val EntranceRiseDp = 8.dp
        val TALL_BODY = 760.dp
        val SHORT_BODY = 240.dp
        const val LARGE = 100_000f
        const val UP = 120f

        val CHIPS = listOf("Key numbers", "Explain simply", "The catch?")
        const val OLD_QUESTION = "How much would I save per year?"
        const val OLD_ANSWER = "The article puts it at \$300 to \$900 a year."
        const val NEW_QUESTION = "Does it work below freezing?"
        const val NEW_ANSWER = "Yes, cold climate models keep heating down to about -25 C."
        val LONG_ANSWER = List(12) { "Cold climate heat pumps keep working well below freezing." }.joinToString(" ")

        fun summarized(turns: List<QaTurn> = emptyList()) = AskPageUiState(
            title = "Why heat pumps are winning in cold climates",
            host = "daylightjournal.com",
            summary = SummaryState.Done(
                text = "- Heat pumps now work in the cold.",
                bullets = persistentListOf("Heat pumps now work in the cold."),
                feedback = MessageFeedback.NONE,
                messageId = 1,
            ),
            turns = turns.toImmutableList(),
            showChips = turns.isEmpty(),
            inputEnabled = true,
        )

        val LongSummary = List(60) { "Heat pumps now work in the cold." }.let { bullets ->
            SummaryState.Done(
                text = bullets.joinToString("\n") { "- $it" },
                bullets = bullets.toImmutableList(),
                feedback = MessageFeedback.NONE,
                messageId = 1,
            )
        }

        fun streaming(answer: String) = summarized(
            turns = listOf(
                QaTurn(OLD_QUESTION, OLD_ANSWER, persistentListOf(), stopped = false),
                QaTurn(NEW_QUESTION, answer, persistentListOf(), stopped = false),
            ),
        ).copy(generating = true)

        val TabsReady = AskTabsUiState(
            tabCount = 3,
            overview = TabsOverviewState.Ready(
                intro = "You have three tabs about heat pumps.",
                groups = persistentListOf(),
                feedback = MessageFeedback.NONE,
                messageId = 1,
                followUps = persistentListOf("Which is cheapest?"),
            ),
        )

        val PageActions = AskPageActions(
            onClose = {},
            onSend = {},
            onStop = {},
            onChip = {},
            onRetry = {},
            onCopy = {},
            onGood = {},
            onPoor = {},
            onCitation = {},
        )

        val TabsActions = AskTabsActions(
            onClose = {},
            onSend = {},
            onStop = {},
            onChip = {},
            onRetry = {},
            onCopy = {},
            onGood = {},
            onPoor = {},
            onOpenGroup = {},
        )
    }
}
