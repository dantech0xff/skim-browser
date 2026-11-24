package com.dantech.skimbrowser.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp

/**
 * Taps this node [inset] in from its end edge, on its vertical center. For a control that holds a
 * smaller button at its center: Robolectric's text has no width without native graphics, so the
 * pill's address field and the collapsed chip center their mark where a plain click lands.
 */
fun SemanticsNodeInteraction.clickNearEnd(inset: Float = 12f): SemanticsNodeInteraction =
    performTouchInput { click(centerRight - Offset(inset.dp.toPx(), 0f)) }

/** Taps this node [inset] in from its start edge, on its vertical center. */
fun SemanticsNodeInteraction.clickNearStart(inset: Float = 8f): SemanticsNodeInteraction =
    performTouchInput { click(centerLeft + Offset(inset.dp.toPx(), 0f)) }
