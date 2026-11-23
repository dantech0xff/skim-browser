package com.dantech.skimbrowser.ui.browsing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.TabGroup
import com.dantech.skimbrowser.domain.tab.TabNeighbor
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Draws [content] (the browser bar) and, while a horizontal drag is past the touch slop, the tab
 * it would open. The bar nudges with the finger and settles with the expressive spatial spring.
 * The page itself is not moved: the WebView swaps once, when the drag commits.
 *
 * A tap still reaches the buttons. The drag is claimed only after it is clearly horizontal.
 * With Remove animations on, the tab changes as soon as the drag commits and the bar does not travel.
 *
 * The caption fades in and out above the bar; the bar does not move for it. Each time the drag
 * passes the point where a release opens a tab that exists, the phone gives a threshold haptic;
 * the release itself gives none. Haptics stay with Remove animations on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabStripSwipe(
    previous: TabNeighbor?,
    next: TabNeighbor?,
    groups: List<TabGroup>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // The id under the finger. The caption reads the current neighbor, so a title or group
    // that arrives during the drag is shown without restarting the gesture.
    var captionId by remember { mutableStateOf<String?>(null) }
    // The last caption shown, kept on screen while it fades out.
    var heldCaptionId by remember { mutableStateOf<String?>(null) }
    val latestPrevious = rememberUpdatedState(previous)
    val latestNext = rememberUpdatedState(next)
    val latestSelect = rememberUpdatedState(onSelect)
    val latestReduced = rememberUpdatedState(reduced)
    val latestSpatial = rememberUpdatedState(spatial)
    val latestHaptics = rememberUpdatedState(LocalHapticFeedback.current)
    val caption = neighborWithId(captionId, previous, next)
    val heldCaption = caption ?: neighborWithId(heldCaptionId, previous, next)
    val density = LocalDensity.current
    val travel = with(density) { Travel.toPx() }
    val threshold = with(density) { Threshold.toPx() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = caption != null,
            enter = if (reduced) EnterTransition.None else fadeIn(effects),
            exit = if (reduced) ExitTransition.None else fadeOut(effects),
            label = "tab swipe caption",
        ) {
            heldCaption?.let { neighbor ->
                TabSwipeCaption(neighbor, groups, forward = neighbor.id == next?.id, Modifier.padding(bottom = 8.dp))
            }
        }
        content(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var totalX = 0f
                            var totalY = 0f
                            var dragging = false
                            var committing = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val before = latestPrevious.value
                                val after = latestNext.value
                                if (change == null || !change.pressed) {
                                    if (dragging) {
                                        launchSettle(
                                            scope, totalX, threshold, latestReduced.value, before, after, travel,
                                            latestSpatial.value, offset, latestSelect.value,
                                        ) { captionId = null }
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                totalX += delta.x
                                totalY += delta.y
                                if (!dragging) {
                                    if (abs(totalY) > slop && abs(totalY) > abs(totalX)) break
                                    if (abs(totalX) > slop && abs(totalX) > abs(totalY)) dragging = true
                                }
                                if (!dragging) continue
                                change.consume()
                                scope.launch { offset.snapTo(visualOffset(totalX, before, after, travel)) }
                                val id = neighborFor(totalX, before, after)?.id
                                captionId = id
                                if (id != null) heldCaptionId = id
                                val commits = committedNeighbor(totalX, threshold, before, after) != null
                                if (commits && !committing) {
                                    latestHaptics.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                                committing = commits
                            }
                        }
                    } finally {
                        // A restart cancels this handler mid-drag. Put the bar back; the tab
                        // change, if the drag had already committed, already happened.
                        captionId = null
                        scope.launch { offset.snapTo(0f) }
                    }
                },
        )
    }
}

@Composable
private fun TabSwipeCaption(
    neighbor: TabNeighbor,
    groups: List<TabGroup>,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    val page = neighbor.title.ifBlank { stringResource(R.string.new_tab) }
    val group = groupLabel(neighbor, groups)
    val description = when {
        group != null && forward -> stringResource(R.string.tab_swipe_next_group, group, page)
        group != null -> stringResource(R.string.tab_swipe_previous_group, group, page)
        forward -> stringResource(R.string.tab_swipe_next, page)
        else -> stringResource(R.string.tab_swipe_previous, page)
    }
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (group != null) {
                Text(
                    group,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(page, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun groupLabel(neighbor: TabNeighbor, groups: List<TabGroup>): String? = when {
    !neighbor.changedGroup -> null
    neighbor.groupId == null -> stringResource(R.string.tabs_group_other)
    else -> groups.firstOrNull { it.id == neighbor.groupId }?.title
}

private val Travel = 48.dp
private val Threshold = 56.dp

private fun neighborFor(totalX: Float, previous: TabNeighbor?, next: TabNeighbor?): TabNeighbor? = when {
    totalX < 0f -> next
    totalX > 0f -> previous
    else -> null
}

/** The tab a release at [totalX] opens: the neighbor in that direction, once the drag is past [threshold]. */
private fun committedNeighbor(totalX: Float, threshold: Float, previous: TabNeighbor?, next: TabNeighbor?): TabNeighbor? =
    neighborFor(totalX, previous, next)?.takeIf { abs(totalX) >= threshold }

private fun neighborWithId(id: String?, previous: TabNeighbor?, next: TabNeighbor?): TabNeighbor? = when (id) {
    null -> null
    next?.id -> next
    previous?.id -> previous
    else -> null
}

/** A drag toward a missing neighbor stays put. Finger left (negative) opens the next tab. */
private fun visualOffset(totalX: Float, previous: TabNeighbor?, next: TabNeighbor?, travel: Float): Float = when {
    totalX < 0f && next == null -> 0f
    totalX > 0f && previous == null -> 0f
    else -> totalX.coerceIn(-travel, travel)
}

private fun launchSettle(
    scope: CoroutineScope,
    totalX: Float,
    threshold: Float,
    reduced: Boolean,
    previous: TabNeighbor?,
    next: TabNeighbor?,
    travel: Float,
    spatial: FiniteAnimationSpec<Float>,
    offset: Animatable<Float, AnimationVector1D>,
    onSelect: (String) -> Unit,
    clearCaption: () -> Unit,
) {
    scope.launch { settle(totalX, threshold, reduced, previous, next, travel, spatial, offset, onSelect, clearCaption) }
}

private suspend fun settle(
    totalX: Float,
    threshold: Float,
    reduced: Boolean,
    previous: TabNeighbor?,
    next: TabNeighbor?,
    travel: Float,
    spatial: FiniteAnimationSpec<Float>,
    offset: Animatable<Float, AnimationVector1D>,
    onSelect: (String) -> Unit,
    clearCaption: () -> Unit,
) {
    val chosen = committedNeighbor(totalX, threshold, previous, next)
    // Select before the spring. A later touch can cancel the animation; it must not drop the tab.
    if (chosen != null) onSelect(chosen.id)
    clearCaption()
    if (chosen != null && !reduced) offset.animateTo(if (totalX < 0f) -travel else travel, spatial)
    if (reduced || offset.value == 0f) offset.snapTo(0f) else offset.animateTo(0f, spatial)
}
