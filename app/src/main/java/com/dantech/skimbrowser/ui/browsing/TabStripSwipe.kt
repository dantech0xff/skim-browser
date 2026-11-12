package com.dantech.skimbrowser.ui.browsing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
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
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // The id under the finger. The caption reads the current neighbor, so a title or group
    // that arrives during the drag is shown without restarting the gesture.
    var captionId by remember { mutableStateOf<String?>(null) }
    val latestPrevious = rememberUpdatedState(previous)
    val latestNext = rememberUpdatedState(next)
    val latestSelect = rememberUpdatedState(onSelect)
    val latestReduced = rememberUpdatedState(reduced)
    val latestSpatial = rememberUpdatedState(spatial)
    val caption = when (captionId) {
        null -> null
        next?.id -> next
        previous?.id -> previous
        else -> null
    }
    val density = LocalDensity.current
    val travel = with(density) { Travel.toPx() }
    val threshold = with(density) { Threshold.toPx() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        caption?.let { neighbor ->
            TabSwipeCaption(neighbor, groups, forward = neighbor.id == next?.id, Modifier.padding(bottom = 8.dp))
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
                                captionId = neighborFor(totalX, before, after)?.id
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
    val chosen = neighborFor(totalX, previous, next)?.takeIf { abs(totalX) >= threshold }
    // Select before the spring. A later touch can cancel the animation; it must not drop the tab.
    if (chosen != null) onSelect(chosen.id)
    clearCaption()
    if (chosen != null && !reduced) offset.animateTo(if (totalX < 0f) -travel else travel, spatial)
    if (reduced || offset.value == 0f) offset.snapTo(0f) else offset.animateTo(0f, spatial)
}
