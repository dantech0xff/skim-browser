package com.dantech.skimbrowser.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.unit.dp

/** The smallest touch area Skim allows, even where the drawn control is 40, 36 or 32. */
private val MinTouchTarget = 48.dp

/**
 * Asserts the accessibility rules every screen follows, on the merged semantics tree that TalkBack
 * reads: each node a user can tap or toggle has a label (text or content description; a link inside
 * a text is labelled by the words it covers), and a touch area of at least 48 by 48 dp.
 *
 * The touch area is the node's touch bounds, the area that accepts its touches: Compose widens the
 * touch bounds of a clickable smaller than 48 dp to the minimum touch target, so a drawn 36 dp
 * button passes, while a toggle without a click action keeps its drawn bounds and fails under 48.
 *
 * Every screenshot test calls it after setting its content.
 */
fun SemanticsNodeInteractionsProvider.assertA11y() {
    val actionable = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
    ).fetchSemanticsNodes()
    val problems = actionable.flatMap(::problemsOf)
    if (problems.isNotEmpty()) {
        throw AssertionError(
            "Accessibility rules not met:\n" + problems.joinToString("\n") { "  - $it" },
        )
    }
}

private fun problemsOf(node: SemanticsNode): List<String> = buildList {
    val label = labelOf(node)
    val name = label.ifEmpty { "node #${node.id}" }
    if (label.isEmpty()) add("$name at ${node.boundsInRoot} has no text or content description")

    val density = node.layoutInfo.density
    val touch = node.touchBoundsInRoot
    val minPx = with(density) { MinTouchTarget.toPx() }
    // Half a pixel of tolerance for bounds that land between pixels.
    if (touch.width < minPx - 0.5f || touch.height < minPx - 0.5f) {
        val width = with(density) { touch.width.toDp() }
        val height = with(density) { touch.height.toDp() }
        add("\"$name\" has a touch area of $width by $height, under $MinTouchTarget")
    }
}

private fun labelOf(node: SemanticsNode): String {
    val config = node.config
    val parts = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)
    val label = parts.filter { it.isNotBlank() }.joinToString(", ")
    return label.ifEmpty { inlineLinkText(node) }
}

/**
 * The words a link inside a text covers ("Terms of Service" in 01's legal line). Compose gives each
 * link of a text a node of its own, in the order of the links, without text: TalkBack reads it as a
 * link of the text that holds it.
 */
private fun inlineLinkText(node: SemanticsNode): String {
    if (SemanticsProperties.LinkTestMarker !in node.config) return ""
    val holder = node.parent ?: return ""
    val links = holder.config.getOrNull(SemanticsProperties.Text).orEmpty().flatMap { text ->
        text.getLinkAnnotations(0, text.length).map { text.text.substring(it.start, it.end) }
    }
    val index = holder.children.filter { SemanticsProperties.LinkTestMarker in it.config }.indexOfFirst { it.id == node.id }
    return links.getOrNull(index)?.takeIf { it.isNotBlank() }.orEmpty()
}
