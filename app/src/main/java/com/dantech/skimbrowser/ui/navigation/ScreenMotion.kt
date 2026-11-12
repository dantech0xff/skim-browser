package com.dantech.skimbrowser.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * A whole-screen change moves with [spatial] and fades with the effects spring (no overshoot).
 * A sheet or a dialog is an overlay and keeps its own motion, so the screen under it does not
 * slide. The theme and FLAG_SECURE follow the back stack in the same composition that starts this
 * transition. They are not waiting on the spring.
 */
internal fun screenTransition(
    overlay: Boolean,
    forward: Boolean,
    spatial: FiniteAnimationSpec<IntOffset>,
): ContentTransform {
    if (overlay) return EnterTransition.None togetherWith ExitTransition.None
    // defaultEffectsSpec: damping 1, stiffness 1600. Effects springs do not overshoot.
    val effects = spring<Float>(dampingRatio = 1f, stiffness = 1600f)
    val slideIn = if (forward) slideInHorizontally(spatial) { it } else slideInHorizontally(spatial) { -it / 4 }
    val slideOut = if (forward) slideOutHorizontally(spatial) { -it / 4 } else slideOutHorizontally(spatial) { it }
    return (slideIn + fadeIn(effects)) togetherWith (slideOut + fadeOut(effects))
}
