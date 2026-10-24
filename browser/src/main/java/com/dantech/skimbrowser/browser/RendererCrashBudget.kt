package com.dantech.skimbrowser.browser

/**
 * How often a tab may recover by itself after its renderer went away: at most [maxRecoveries]
 * times within [windowMs]. Past that the tab shows its error with Try again instead, so a page
 * that kills its renderer on every load (an out-of-memory page) cannot loop forever.
 */
class RendererCrashBudget(
    private val maxRecoveries: Int = 2,
    private val windowMs: Long = 60_000,
) {
    private val crashes = HashMap<String, ArrayDeque<Long>>()

    /**
     * Records a renderer loss of [tabId] at [now] (a monotonic clock, in milliseconds) and returns
     * whether the tab may recover by itself.
     */
    @Synchronized
    fun allow(tabId: String, now: Long): Boolean {
        val times = crashes.getOrPut(tabId) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        times.addLast(now)
        return times.size <= maxRecoveries
    }

    /** Drops what is known about [tabId], once the tab is gone for good. */
    @Synchronized
    fun forget(tabId: String) {
        crashes.remove(tabId)
    }
}
