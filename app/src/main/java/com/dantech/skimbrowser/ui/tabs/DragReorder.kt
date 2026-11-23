package com.dantech.skimbrowser.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.ui.components.TabCardShape
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Where a tab card sits in the grid, in the grid's coordinates. [groupId] null is Other.
 * Headers are not slots.
 */
internal data class ReorderSlot(
    val key: String,
    val groupId: Long?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * The index of [draggedKey] inside its own group after a drop at [pointerX], [pointerY].
 *
 * A drop on a card of another group returns null, so a tab never leaves its group. A drop on empty
 * space lands on the nearest card of the same group. The index counts only that group's cards, in
 * the order of [slots].
 */
internal fun targetIndexInGroup(
    slots: List<ReorderSlot>,
    draggedKey: String,
    groupId: Long?,
    pointerX: Int,
    pointerY: Int,
): Int? {
    val group = slots.filter { it.groupId == groupId }
    if (group.none { it.key == draggedKey }) return null
    val hit = slots.firstOrNull { slot ->
        pointerX in slot.left..slot.right && pointerY in slot.top..slot.bottom
    }
    if (hit != null && hit.groupId != groupId) return null
    val target = hit ?: group.minByOrNull { slot ->
        val dx = (slot.left + slot.right) / 2 - pointerX
        val dy = (slot.top + slot.bottom) / 2 - pointerY
        dx.toLong() * dx + dy.toLong() * dy
    } ?: return null
    return group.indexOfFirst { it.key == target.key }.takeIf { it >= 0 }
}

/**
 * The tab being dragged for reorder, and the index in its group a drop would give it. The grid
 * shows [previewSections] while this is set; it is cleared once the dragged card has settled.
 */
@Immutable
internal data class ReorderPreview(val tabId: String, val toIndex: Int)

/**
 * [sections] with [preview] applied: the dragged tab taken out of its section and put back at
 * [ReorderPreview.toIndex], clamped to the section. Other sections, and every section when the tab
 * is in none of them or would not move, are returned as they are.
 */
internal fun previewSections(
    sections: ImmutableList<TabSection>,
    preview: ReorderPreview?,
): ImmutableList<TabSection> {
    if (preview == null) return sections
    val sectionIndex = sections.indexOfFirst { section -> section.tabs.any { it.id == preview.tabId } }
    if (sectionIndex < 0) return sections
    val section = sections[sectionIndex]
    val from = section.tabs.indexOfFirst { it.id == preview.tabId }
    val to = preview.toIndex.coerceIn(0, section.tabs.lastIndex)
    if (from == to) return sections
    val tabs = section.tabs.toMutableList()
    tabs.add(to, tabs.removeAt(from))
    return sections.toPersistentList().set(sectionIndex, section.copy(tabs = tabs.toImmutableList()))
}

/**
 * Follows a long press on a tab card and reports the index it was dropped on, inside its group.
 * A short drag is left to swipe-to-close: this waits for a long press.
 *
 * While the card is dragged over a card of its own group, [onPreview] receives the index in the
 * group a drop there would give, so the grid can show that order; over another group it receives
 * the card's own index back. It is called only when that index changes, and once at pick up.
 * [indexInGroup] is the index of a tab in its group as the grid shows it now. The card stays under
 * the finger while its slot moves. On a drop at another index [onMove] is called once with it; the
 * card then settles into its slot with the spatial spring and [onPreview] gets null once it has
 * settled. A cancelled drag shows the original order again and calls nothing.
 *
 * The long press that picks the card up gives a long-press haptic and a drop gives a gesture-end
 * haptic; a cancelled drag gives none. While picked up, the card is drawn [LiftScale] times its
 * size with a shadow, and [onLiftChange] is true so the grid can draw it above the others; it
 * turns false once the card has settled back. With Remove animations on, the card lifts and
 * settles at once; the haptics stay.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.tabDragReorder(
    itemKey: String,
    groupId: Long?,
    gridState: LazyGridState,
    enabled: Boolean,
    tabKeys: ImmutableSet<String>,
    groupOf: (String) -> Long?,
    indexInGroup: (String) -> Int?,
    onPreview: (toIndex: Int?) -> Unit,
    onMove: (toIndex: Int) -> Unit,
    onLiftChange: (lifted: Boolean) -> Unit = {},
): Modifier {
    val keys by rememberUpdatedState(tabKeys)
    val groups by rememberUpdatedState(groupOf)
    val shownIndex by rememberUpdatedState(indexInGroup)
    val preview by rememberUpdatedState(onPreview)
    val move by rememberUpdatedState(onMove)
    val grid by rememberUpdatedState(gridState)
    val liftChange by rememberUpdatedState(onLiftChange)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val spatial by rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val scope = rememberCoroutineScope()
    // How far the finger has moved since pick up. Read only in the layer.
    var drag by remember(itemKey) { mutableStateOf(Offset.Zero) }
    // Where the card's slot was at pick up; null while it is not dragged.
    var startSlot by remember(itemKey) { mutableStateOf<IntOffset?>(null) }
    // 0 at rest, 1 picked up.
    val lift = remember(itemKey) { Animatable(0f) }
    // 1 while the card follows the finger, 0 once it sits in its slot.
    val hold = remember(itemKey) { Animatable(0f) }
    var settling by remember(itemKey) { mutableStateOf<Job?>(null) }
    val pickUp = {
        settling?.cancel()
        liftChange(true)
        scope.launch { hold.snapTo(1f) }
        scope.launch { if (reduced) lift.snapTo(1f) else lift.animateTo(1f, spatial) }
    }
    val settle = {
        settling = scope.launch {
            coroutineScope {
                launch { if (reduced) lift.snapTo(0f) else lift.animateTo(0f, spatial) }
                launch { if (reduced) hold.snapTo(0f) else hold.animateTo(0f, spatial) }
            }
            startSlot = null
            drag = Offset.Zero
            liftChange(false)
            // A move the ViewModel did not apply puts the real order back.
            grid.keepScrollPosition()
            preview(null)
        }
    }
    return this
        .pointerInput(itemKey, groupId, enabled) {
            if (!enabled) return@pointerInput
            var startIndex: Int? = null
            var shown: Int? = null
            // Writes a new preview index, keeping the grid scrolled where it is.
            val show = { index: Int ->
                if (index != shown) {
                    shown = index
                    grid.keepScrollPosition()
                    preview(index)
                }
            }
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    drag = Offset.Zero
                    startSlot = grid.slotOf(itemKey)
                    startIndex = shownIndex(itemKey)
                    shown = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    startIndex?.let(show)
                    pickUp()
                },
                onDragCancel = {
                    startIndex?.let(show)
                    settle()
                },
                onDragEnd = {
                    val from = startIndex
                    val to = shown
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    settle()
                    if (from != null && to != null && to != from) move(to)
                },
                onDrag = { change, amount ->
                    change.consume()
                    drag += amount
                    val from = startIndex ?: return@detectDragGesturesAfterLongPress
                    val start = startSlot ?: return@detectDragGesturesAfterLongPress
                    val over = keyUnder(grid, itemKey, groupId, keys, groups, start, drag)
                    show(over?.let(shownIndex) ?: from)
                },
            )
        }
        .graphicsLayer {
            val start = startSlot
            if (start != null) {
                // The slot moves when the preview changes; the card stays where the finger put it.
                val moved = (grid.slotOf(itemKey) ?: start) - start
                val factor = hold.value
                translationX = (drag.x - moved.x) * factor
                translationY = (drag.y - moved.y) * factor
            }
            val raised = lift.value
            if (raised != 0f) {
                val scale = 1f + (LiftScale - 1f) * raised
                scaleX = scale
                scaleY = scale
                shadowElevation = LiftElevation.toPx() * raised.coerceIn(0f, 1f)
                shape = TabCardShape
            }
        }
}

/** How much bigger a picked-up card is drawn. */
internal const val LiftScale = 1.04f
private val LiftElevation = 6.dp

/**
 * Keeps the grid where it is through the next change of order. Without it the grid follows the
 * first visible item to its new place.
 */
private fun LazyGridState.keepScrollPosition() {
    requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
}

/** Where [key]'s item is laid out in the grid now, or null when it is not visible. */
private fun LazyGridState.slotOf(key: String): IntOffset? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset

/**
 * The key of the card of [groupId] a drop would land on, with the pointer at the dragged card's
 * centre at pick up plus [drag], against the slots as laid out now. Null over another group.
 */
private fun keyUnder(
    gridState: LazyGridState,
    itemKey: String,
    groupId: Long?,
    tabKeys: Set<String>,
    groupOf: (String) -> Long?,
    startSlot: IntOffset,
    drag: Offset,
): String? {
    val info = gridState.layoutInfo.visibleItemsInfo
    val dragged = info.firstOrNull { it.key == itemKey } ?: return null
    val slots = info.mapNotNull { item ->
        val key = item.key as? String ?: return@mapNotNull null
        if (key != itemKey && key !in tabKeys) return@mapNotNull null
        val group = if (key == itemKey) groupId else groupOf(key)
        ReorderSlot(
            key = key,
            groupId = group,
            left = item.offset.x,
            top = item.offset.y,
            right = item.offset.x + item.size.width,
            bottom = item.offset.y + item.size.height,
        )
    }
    val pointerX = startSlot.x + dragged.size.width / 2 + drag.x.roundToInt()
    val pointerY = startSlot.y + dragged.size.height / 2 + drag.y.roundToInt()
    val index = targetIndexInGroup(slots, itemKey, groupId, pointerX, pointerY) ?: return null
    return slots.filter { it.groupId == groupId }.getOrNull(index)?.key
}
