package com.dantech.skimbrowser.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenMotionTest {

    private val spatial = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 200f)

    @Test
    fun aWholeScreenChangeMovesAndAnOverlayDoesNot() {
        val forward = screenTransition(overlay = false, forward = true, spatial = spatial)
        val back = screenTransition(overlay = false, forward = false, spatial = spatial)
        val overlay = screenTransition(overlay = true, forward = true, spatial = spatial)

        assertNotEquals(EnterTransition.None, forward.targetContentEnter)
        assertNotEquals(EnterTransition.None, back.targetContentEnter)
        assertNotEquals(forward.targetContentEnter, back.targetContentEnter)
        assertEquals(EnterTransition.None, overlay.targetContentEnter)
        assertEquals(ExitTransition.None, overlay.initialContentExit)
    }
}
