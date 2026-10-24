package com.dantech.skimbrowser.browser

import androidx.annotation.MainThread

/**
 * The steps of Find in page (`WebView.findNext`) of one WebView. A step sent while the WebView is
 * still counting the matches of a new search is answered from the first match again, so it would
 * not move: steps asked for meanwhile wait, and go out in order once the count is done.
 */
@MainThread
class FindSteps {
    private var counting = false
    private val waiting = ArrayDeque<Boolean>()

    /** A new search started: steps asked for before it is counted wait. */
    fun searchStarted() {
        counting = true
        waiting.clear()
    }

    /** True when a step in direction [forward] may go out now; false when it waits for the count. */
    fun step(forward: Boolean): Boolean {
        if (!counting) return true
        waiting.addLast(forward)
        return false
    }

    /**
     * The WebView reported on the search ([doneCounting] from its find listener). Returns the
     * directions of the steps to send now, oldest first: those that waited, once the count is done.
     */
    fun reported(doneCounting: Boolean): List<Boolean> {
        if (!counting || !doneCounting) return emptyList()
        counting = false
        return waiting.toList().also { waiting.clear() }
    }

    /** The search ended: nothing waits any more. */
    fun cleared() {
        counting = false
        waiting.clear()
    }
}
