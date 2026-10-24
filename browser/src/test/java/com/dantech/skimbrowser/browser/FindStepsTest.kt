package com.dantech.skimbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindStepsTest {

    private val steps = FindSteps()

    @Test
    fun withoutASearchBeingCountedAStepGoesOutAtOnce() {
        assertTrue(steps.step(forward = true))

        steps.searchStarted()
        steps.reported(doneCounting = true)

        assertTrue("the count is done", steps.step(forward = false))
        assertEquals("a step's own reports release nothing", emptyList<Boolean>(), steps.reported(doneCounting = true))
    }

    @Test
    fun stepsAskedForWhileASearchIsCountedWaitAndGoOutInOrderOnceItIsDone() {
        steps.searchStarted()

        assertFalse(steps.step(forward = true))
        assertFalse(steps.step(forward = true))
        assertFalse(steps.step(forward = false))
        assertEquals("not counted yet", emptyList<Boolean>(), steps.reported(doneCounting = false))

        assertEquals(listOf(true, true, false), steps.reported(doneCounting = true))
        assertEquals("sent once", emptyList<Boolean>(), steps.reported(doneCounting = true))
        assertTrue(steps.step(forward = true))
    }

    @Test
    fun aNewSearchDropsTheStepsOfTheLastOne() {
        steps.searchStarted()
        steps.step(forward = true)

        steps.searchStarted()

        assertEquals(emptyList<Boolean>(), steps.reported(doneCounting = true))
    }

    @Test
    fun clearingTheSearchDropsWaitingStepsAndStopsWaiting() {
        steps.searchStarted()
        steps.step(forward = true)

        steps.cleared()

        assertTrue("nothing is counted any more, so a step needs no report first", steps.step(forward = true))
        assertEquals(emptyList<Boolean>(), steps.reported(doneCounting = true))
    }
}
