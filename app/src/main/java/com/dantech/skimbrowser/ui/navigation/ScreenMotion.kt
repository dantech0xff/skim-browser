package com.dantech.skimbrowser.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * A whole-screen change moves with [spatial]. A sheet or a dialog is an overlay and keeps its own
 * motion, so the screen under it does not slide. The theme and FLAG_SECURE follow the back stack
 * in the same composition that starts this transition. They are not waiting on the spring.
 */
internal fun screenTransition(
    overlay: Boolean,
    forward: Boolean,
    spatial: FiniteAnimationSpec<IntOffset>,
): ContentTransform {
    if (overlay) return EnterTransition.None togetherWith ExitTransition.None
    return if (forward) {
        slideInHorizontally(spatial) { it } togetherWith slideOutHorizontally(spatial) { -it / 4 }
    } else {
        slideInHorizontally(spatial) { -it / 4 } togetherWith slideOutHorizontally(spatial) { it }
    }
}
