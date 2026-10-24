package com.dantech.skimbrowser.ui.shell

import android.app.Activity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.dantech.skimbrowser.browser.Finger
import com.dantech.skimbrowser.browser.awaitNextFrame
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.viewTree
import com.dantech.skimbrowser.browser.waitUntil

/**
 * What the app's window shows, read as accessibility services read it: the merged semantics of the
 * Compose content in the activity's window, where a button is one node with its label. A tap goes
 * through the system's input pipeline to the middle of a node, as a finger's would.
 */
internal object AppScreen {

    /** Whether a node whose whole text is [text] is on screen. */
    fun shows(activity: Activity, text: String): Boolean = onMain { nodesWithText(activity, text).isNotEmpty() }

    /** The one node whose whole text is [text], once it is on screen. */
    fun awaitNode(activity: Activity, text: String): SemanticsNode {
        waitUntil("\"$text\" to show") { shows(activity, text) }
        return onMain { nodesWithText(activity, text).single() }
    }

    /**
     * Taps the middle of the one node whose whole text is [text], once it is on screen and stays
     * put: a node that animates in (a snackbar grows into place) moves between frames, and a tap
     * aimed at where it was can miss it.
     */
    fun tap(activity: Activity, text: String) {
        awaitNode(activity, text)
        val decor = activity.window.decorView
        var bounds = boundsOf(activity, text)
        var still = 0
        waitUntil("\"$text\" to stop moving") {
            awaitNextFrame()
            val now = boundsOf(activity, text)
            still = if (now == bounds) still + 1 else 0
            bounds = now
            still >= STILL_FRAMES
        }
        Finger.tap(decor, bounds.center.x / decor.width, bounds.center.y / decor.height)
    }

    /** How the one node showing [text], or the nearest node around it that has one, announces itself. */
    fun liveRegionOf(activity: Activity, text: String): LiveRegionMode? = onMain {
        generateSequence(nodesWithText(activity, text).single()) { it.parent }
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.LiveRegion) }
    }

    /** The nodes on screen whose text, all of it, is [text]; call on the main thread. */
    private fun nodesWithText(activity: Activity, text: String): List<SemanticsNode> =
        viewTree(activity.window.decorView)
            .filterIsInstance<ViewRootForTest>()
            .flatMap { root -> root.semanticsOwner.rootSemanticsNode.withDescendants() }
            .filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } == text }

    private fun boundsOf(activity: Activity, text: String): Rect = onMain { nodesWithText(activity, text).single().boundsInWindow }

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.withDescendants() }

    /** Frames in a row a node keeps the same bounds before it counts as still. */
    private const val STILL_FRAMES = 3
}
