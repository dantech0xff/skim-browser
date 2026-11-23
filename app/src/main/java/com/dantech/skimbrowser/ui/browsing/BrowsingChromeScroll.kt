package com.dantech.skimbrowser.ui.browsing

/**
 * Collapses the browsing pill to its chip after the page moves down, and restores it after the page
 * moves back up. A short move does nothing. At the top of the page the pill is full.
 *
 * Only a move toward the other state counts: down while the pill is full, up while it is the chip.
 * So a pill brought back by a tap on the chip stays until the page moves down that far again.
 *
 * [onScrolled] returns the new collapsed value, or null when the pill should stay as it is.
 * [dy] is the change in scroll Y: positive moves down the page.
 */
internal class BrowsingChromeScroll(
    private val thresholdPx: Int,
) {
    private var run = 0

    fun onScrolled(scrollY: Int, dy: Int, collapsed: Boolean): Boolean? {
        if (dy == 0) return null
        if (scrollY <= 0) {
            run = 0
            return if (collapsed) false else null
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
}
