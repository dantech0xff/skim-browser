package com.dantech.skimbrowser.browser

import android.content.ComponentCallbacks2

/**
 * Which tabs keep a live WebView: the pool's rules, apart from the WebViews themselves.
 *
 * At most [capacity] WebViews stay alive, the least recently used going first: 3, or 1 on a
 * low-RAM device, after a critical memory warning and after the system killed a renderer to free
 * memory, until the Activity starts again. The capacity is soft: a tab that must keep its WebView
 * is never chosen, even if that leaves more alive than the capacity. That is the current tab, the
 * WebView just made, and the newest [MAX_PINNED_WINDOWS] popup windows together with the tab that
 * opened each, kept while both sides are alive, so a popup can still talk to its opener (sign-in
 * and payment windows). Older windows are plain tabs: a page that opens many of them (every link
 * that targets a new tab is one) cannot keep them all alive.
 *
 * A memory warning that the app is hidden or in the background only saves the WebViews' state:
 * evicting there would destroy the opener of a sign-in popup while the user fetches a code.
 */
class WebViewPoolPolicy(private val isLowRamDevice: Boolean) {

    /** What a memory warning asks of the pool. */
    enum class TrimResponse {
        NONE,

        /** Save every WebView's state; keep them all. */
        SAVE_STATE,

        /** Save the state, drop to a capacity of 1 and evict down to it. */
        EVICT,
    }

    /** The tabs with a live WebView, least recently used first. */
    private val order = LinkedHashSet<String>()

    /** Popup tab to the tab that opened it, the oldest window first. */
    private val openers = LinkedHashMap<String, String>()

    var capacity: Int = fullCapacity()
        private set

    /** The tabs with a live WebView, least recently used first. */
    fun liveTabs(): List<String> = order.toList()

    /** [tabId]'s WebView was just made or used: it becomes the most recently used. */
    fun touch(tabId: String) {
        order.remove(tabId)
        order.add(tabId)
    }

    /** [tabId]'s WebView is gone; a popup relation it was part of ends with it. */
    fun remove(tabId: String) {
        order.remove(tabId)
        openers.remove(tabId)
        openers.values.removeAll { it == tabId }
    }

    /** [popupTabId]'s WebView shows a window that [openerTabId]'s page opened; it is the newest window. */
    fun pinPopup(popupTabId: String, openerTabId: String) {
        if (popupTabId == openerTabId) return
        openers.remove(popupTabId)
        openers[popupTabId] = openerTabId
    }

    /** True while [tabId] is a popup or an opener of one of the newest windows whose two sides are both live. */
    fun isPinned(tabId: String): Boolean = tabId in pinnedTabs()

    private fun pinnedTabs(): Set<String> {
        val windows = openers.entries.filter { (popup, opener) -> popup in order && opener in order }
        return windows.takeLast(MAX_PINNED_WINDOWS).flatMapTo(HashSet()) { listOf(it.key, it.value) }
    }

    /**
     * The tabs whose WebViews to evict now, least recently used first, so that at most [capacity]
     * stay alive: never one in [keep] nor a pinned one.
     */
    fun evictions(keep: Set<String>): List<String> {
        var excess = order.size - capacity
        if (excess <= 0) return emptyList()
        val pinned = pinnedTabs()
        val evicted = ArrayList<String>(excess)
        for (tabId in order) {
            if (excess == 0) break
            if (tabId in keep || tabId in pinned) continue
            evicted += tabId
            excess--
        }
        return evicted
    }

    /**
     * The answer to `onTrimMemory([level])`, with [lowMemory] from `ActivityManager.MemoryInfo`.
     * Critical levels and low memory drop the capacity to 1.
     */
    fun onTrimMemory(level: Int, lowMemory: Boolean): TrimResponse = when {
        lowMemory || isCritical(level) -> {
            capacity = 1
            TrimResponse.EVICT
        }
        level >= RUNNING_MODERATE -> TrimResponse.SAVE_STATE
        else -> TrimResponse.NONE
    }

    /** The system killed a renderer to free memory: one WebView until the Activity starts again. */
    fun onRendererKilled() {
        capacity = 1
    }

    /** The Activity started: the full capacity is back, except on a low-RAM device. */
    fun onStart() {
        capacity = fullCapacity()
    }

    private fun fullCapacity() = if (isLowRamDevice) 1 else FULL_CAPACITY

    companion object {
        const val FULL_CAPACITY = 3

        /** How many of the newest popup windows keep their two tabs alive. */
        const val MAX_PINNED_WINDOWS = 2

        // The running levels and COMPLETE are deprecated because the system stopped sending them to
        // apps from API 34, but earlier versions still send them and they are the critical ones.
        @Suppress("DEPRECATION")
        private const val RUNNING_MODERATE = ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE

        @Suppress("DEPRECATION")
        private const val RUNNING_CRITICAL = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL

        @Suppress("DEPRECATION")
        private const val COMPLETE = ComponentCallbacks2.TRIM_MEMORY_COMPLETE

        private fun isCritical(level: Int) = level == RUNNING_CRITICAL || level >= COMPLETE
    }
}
