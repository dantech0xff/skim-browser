package com.dantech.skimbrowser.ui.tabs

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableSet

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
 * Follows a long press on a tab card and reports the index it was dropped on, inside its group.
 * A short drag is left to swipe-to-close: this waits for a long press. [groupOf] and [onMove] are
 * read when the finger lifts.
 */
@Composable
internal fun Modifier.tabDragReorder(
    itemKey: String,
    groupId: Long?,
    gridState: LazyGridState,
    enabled: Boolean,
    tabKeys: ImmutableSet<String>,
    groupOf: (String) -> Long?,
    onMove: (toIndex: Int) -> Unit,
): Modifier {
    val keys by rememberUpdatedState(tabKeys)
    val groups by rememberUpdatedState(groupOf)
    val move by rememberUpdatedState(onMove)
    val grid by rememberUpdatedState(gridState)
    var translation by remember(itemKey) { mutableStateOf(IntOffset.Zero) }
    return this
        .graphicsLayer {
            translationX = translation.x.toFloat()
            translationY = translation.y.toFloat()
        }
        .pointerInput(itemKey, groupId, enabled) {
            if (!enabled) return@pointerInput
            var deltaX = 0f
            var deltaY = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    deltaX = 0f
                    deltaY = 0f
                },
                onDragCancel = {
                    deltaX = 0f
                    deltaY = 0f
                    translation = IntOffset.Zero
                },
                onDragEnd = {
                    val index = dropIndex(grid, itemKey, groupId, keys, groups, deltaX, deltaY)
                    deltaX = 0f
                    deltaY = 0f
                    translation = IntOffset.Zero
                    if (index != null) move(index)
                },
                onDrag = { change, drag ->
                    change.consume()
                    deltaX += drag.x
                    deltaY += drag.y
                    translation = IntOffset(deltaX.roundToInt(), deltaY.roundToInt())
                },
            )
        }
}

private fun dropIndex(
    gridState: LazyGridState,
    itemKey: String,
    groupId: Long?,
    tabKeys: Set<String>,
    groupOf: (String) -> Long?,
    deltaX: Float,
    deltaY: Float,
): Int? {
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
    val pointerX = dragged.offset.x + dragged.size.width / 2 + deltaX.roundToInt()
    val pointerY = dragged.offset.y + dragged.size.height / 2 + deltaY.roundToInt()
    val index = targetIndexInGroup(slots, itemKey, groupId, pointerX, pointerY) ?: return null
    val current = slots.filter { it.groupId == groupId }.indexOfFirst { it.key == itemKey }
    return index.takeIf { it != current }
}
