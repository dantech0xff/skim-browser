package com.dantech.skimbrowser.ui.browsing

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Save video keeps its place in the pill, and when More options offers it instead. */
class PillRoomTest {

    @Test
    fun aPhoneOf360DpKeepsSaveVideoInThePill() {
        assertTrue(saveVideoFitsInPill(360.dp, fontScale = 1f))
    }

    @Test
    fun aPhoneOf320DpLeavesItToMoreOptions() {
        assertFalse(saveVideoFitsInPill(320.dp, fontScale = 1f))
    }

    @Test
    fun largerTextNeedsMoreRoomForTheHost() {
        assertTrue(saveVideoFitsInPill(412.dp, fontScale = 1.3f))
        assertFalse(saveVideoFitsInPill(360.dp, fontScale = 1.3f))
    }

    @Test
    fun smallerTextDoesNotLowerTheMinimum() {
        assertFalse(saveVideoFitsInPill(320.dp, fontScale = 0.85f))
    }
}
