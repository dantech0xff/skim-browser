package com.dantech.skimbrowser.ui.browsing

import android.os.SystemClock

/**
 * Collapses the browsing pill to its chip after the page moves down, and restores it after the page
 * moves back up. A short move does nothing. At the top of the page the pill is full.
 *
 * Only a move toward the other state counts: down while the pill is full, up while it is the chip.
 * So a pill brought back by a tap on the chip stays until the page moves down that far again. The
 * tap does not stop a fling, so [onExpandedByTap] also holds the pill through the move under way.
 *
 * [onScrolled] returns the new collapsed value, or null when the pill should stay as it is.
 * [dy] is the change in scroll Y: positive moves down the page.
 */
internal class BrowsingChromeScroll(
    private val thresholdPx: Int,
    private val uptimeMs: () -> Long = SystemClock::uptimeMillis,
) {
    private var run = 0

    /** When the page last moved while a tap on the chip holds the pill; null when none does. */
    private var heldAt: Long? = null

    /**
     * The chip was tapped and the pill is back. Moves down do not count until the page has been
     * still for [STILL_MS] or has moved up: the fling the tap did not stop is not a new move.
     */
    fun onExpandedByTap() {
        run = 0
        heldAt = uptimeMs()
    }

    fun onScrolled(scrollY: Int, dy: Int, collapsed: Boolean): Boolean? {
        if (dy == 0) return null
        if (scrollY <= 0) {
            run = 0
            heldAt = null
            return if (collapsed) false else null
        }
        heldAt?.let { last ->
            val now = uptimeMs()
            if (dy > 0 && !collapsed && now - last < STILL_MS) {
                heldAt = now
                return null
            }
            heldAt = null
        }
        if (collapsed == dy > 0) {
            run = 0
            return null
        }
        val continuing = (dy > 0 && run > 0) || (dy < 0 && run < 0)
        run = if (continuing) run + dy else dy
        return when {
            !collapsed && run >= thresholdPx -> {
                run = 0
                true
            }
            collapsed && run <= -thresholdPx -> {
                run = 0
                false
            }
            else -> null
        }
    }

    private companion object {
        /** A page that has not moved for this long has come to rest. Frames of a fling are 16 ms apart. */
        const val STILL_MS = 150L
    }
}
