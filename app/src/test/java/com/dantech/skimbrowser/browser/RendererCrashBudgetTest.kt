package com.dantech.skimbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererCrashBudgetTest {

    private val budget = RendererCrashBudget()

    @Test
    fun aTabRecoversTwiceInAMinuteAndNotAThirdTime() {
        assertTrue(budget.allow("a", now = 1_000))
        assertTrue(budget.allow("a", now = 2_000))
        assertFalse(budget.allow("a", now = 3_000))
        assertFalse(budget.allow("a", now = 59_999))
    }

    @Test
    fun aCrashOlderThanTheWindowNoLongerCounts() {
        assertTrue(budget.allow("a", now = 0))
        assertTrue(budget.allow("a", now = 30_000))

        // Exactly 60 s after the first crash it has left the window; the second one still counts.
        assertTrue(budget.allow("a", now = 60_000))
        assertFalse(budget.allow("a", now = 60_001))
    }

    @Test
    fun aCrashJustInsideTheWindowStillCounts() {
        assertTrue(budget.allow("a", now = 0))
        assertTrue(budget.allow("a", now = 1))

        assertFalse(budget.allow("a", now = 59_999))
    }

    @Test
    fun tabsHaveTheirOwnBudget() {
        assertTrue(budget.allow("a", now = 0))
        assertTrue(budget.allow("a", now = 1))
        assertFalse(budget.allow("a", now = 2))

        assertTrue(budget.allow("b", now = 3))
        assertTrue(budget.allow("b", now = 4))
    }

    @Test
    fun aForgottenTabStartsOver() {
        repeat(3) { budget.allow("a", now = it.toLong()) }

        budget.forget("a")

        assertTrue(budget.allow("a", now = 10))
    }
}
