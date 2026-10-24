package com.dantech.skimbrowser.browser

import android.content.ComponentCallbacks2
import com.dantech.skimbrowser.browser.WebViewPoolPolicy.TrimResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPoolPolicyTest {

    @Test
    fun threeWebViewsStayAliveAndTheLeastRecentlyUsedGoesFirst() {
        val policy = policyWith("a", "b", "c", "d")

        assertEquals(3, policy.capacity)
        assertEquals(listOf("a"), policy.evictions(keep = emptySet()))
    }

    @Test
    fun usingAWebViewMakesItTheMostRecentlyUsed() {
        val policy = policyWith("a", "b", "c", "d")

        policy.touch("a")

        assertEquals(listOf("b", "c", "d", "a"), policy.liveTabs())
        assertEquals(listOf("b"), policy.evictions(keep = emptySet()))
    }

    @Test
    fun aKeptTabIsSkippedAndTheNextOldestGoesInstead() {
        val policy = policyWith("current", "b", "c", "just-made", "e")

        assertEquals(listOf("b", "c"), policy.evictions(keep = setOf("current", "just-made")))
    }

    @Test
    fun nothingIsEvictedWithinTheCapacity() {
        assertEquals(emptyList<String>(), policyWith("a", "b", "c").evictions(keep = emptySet()))
        assertEquals(emptyList<String>(), policyWith().evictions(keep = emptySet()))
    }

    @Test
    fun aPopupAndItsOpenerAreNeverEvictedWhileBothLive() {
        val policy = policyWith("opener", "popup", "b", "c", "d")
        policy.pinPopup(popupTabId = "popup", openerTabId = "opener")

        assertTrue(policy.isPinned("opener"))
        assertTrue(policy.isPinned("popup"))
        assertEquals(listOf("b", "c"), policy.evictions(keep = emptySet()))
    }

    @Test
    fun onlyTheNewestPopupWindowsKeepTheirTabsAlive() {
        val policy = policyWith("opener", "first", "second", "third", "other")
        policy.pinPopup(popupTabId = "first", openerTabId = "opener")
        policy.pinPopup(popupTabId = "second", openerTabId = "opener")
        policy.pinPopup(popupTabId = "third", openerTabId = "opener")

        assertFalse("the oldest window is a plain tab again", policy.isPinned("first"))
        assertTrue(policy.isPinned("second"))
        assertTrue(policy.isPinned("third"))
        assertTrue(policy.isPinned("opener"))
        assertEquals(listOf("first", "other"), policy.evictions(keep = emptySet()))
    }

    @Test
    fun anOlderWindowCountsAgainOnceANewerOneEnds() {
        val policy = policyWith("opener", "first", "second", "third", "other")
        policy.pinPopup(popupTabId = "first", openerTabId = "opener")
        policy.pinPopup(popupTabId = "second", openerTabId = "opener")
        policy.pinPopup(popupTabId = "third", openerTabId = "opener")

        policy.remove("third")

        assertTrue(policy.isPinned("first"))
        assertTrue(policy.isPinned("second"))
    }

    @Test
    fun aWindowOpenedAgainFromTheSamePopupIsTheNewestOne() {
        val policy = policyWith("opener", "first", "second", "third")
        policy.pinPopup(popupTabId = "first", openerTabId = "opener")
        policy.pinPopup(popupTabId = "second", openerTabId = "opener")
        policy.pinPopup(popupTabId = "first", openerTabId = "opener")
        policy.pinPopup(popupTabId = "third", openerTabId = "opener")

        assertTrue(policy.isPinned("first"))
        assertFalse("the one left alone longest drops out", policy.isPinned("second"))
    }

    @Test
    fun theCapacityGivesWayWhenEveryWebViewMustStay() {
        val policy = policyWith("opener", "popup", "current", "just-made")
        policy.pinPopup(popupTabId = "popup", openerTabId = "opener")

        assertEquals(emptyList<String>(), policy.evictions(keep = setOf("current", "just-made")))
    }

    @Test
    fun aPopupRelationEndsWithTheWebViewOfEitherSide() {
        val closed = policyWith("opener", "popup", "b", "c")
        closed.pinPopup(popupTabId = "popup", openerTabId = "opener")
        closed.remove("popup")
        assertFalse("the opener's popup is gone", closed.isPinned("opener"))
        assertEquals(listOf("opener", "b", "c"), closed.liveTabs())

        val openerGone = policyWith("opener", "popup", "b", "c")
        openerGone.pinPopup(popupTabId = "popup", openerTabId = "opener")
        openerGone.remove("opener")
        assertFalse("the popup's opener is gone", openerGone.isPinned("popup"))
        openerGone.touch("d")
        assertEquals(listOf("popup"), openerGone.evictions(keep = emptySet()))
    }

    @Test
    fun aNewWebViewOfEitherSideIsNoPartOfTheOldWindow() {
        // The window relation lived in the WebViews that are gone; new ones are plain tabs.
        val opener = policyWith("opener", "popup", "b")
        opener.pinPopup(popupTabId = "popup", openerTabId = "opener")
        opener.remove("opener")
        opener.touch("opener")
        assertFalse(opener.isPinned("popup"))
        assertFalse(opener.isPinned("opener"))

        val popup = policyWith("opener", "popup", "b")
        popup.pinPopup(popupTabId = "popup", openerTabId = "opener")
        popup.remove("popup")
        popup.touch("popup")
        assertFalse(popup.isPinned("opener"))
        assertFalse(popup.isPinned("popup"))
    }

    @Test
    fun aRemovedTabNoLongerCounts() {
        val policy = policyWith("a", "b", "c", "d")

        policy.remove("a")

        assertEquals(listOf("b", "c", "d"), policy.liveTabs())
        assertEquals(emptyList<String>(), policy.evictions(keep = emptySet()))
    }

    @Test
    fun aLowRamDeviceKeepsOneWebViewEvenAfterTheActivityStarts() {
        val policy = WebViewPoolPolicy(isLowRamDevice = true)
        listOf("a", "b", "c").forEach(policy::touch)

        assertEquals(1, policy.capacity)
        assertEquals(listOf("a", "b"), policy.evictions(keep = emptySet()))
        policy.onStart()
        assertEquals(1, policy.capacity)
    }

    @Test
    fun beingHiddenOrInTheBackgroundOnlySavesTheState() {
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            trimLevel(MODERATE),
            trimLevel(RUNNING_LOW),
            trimLevel(RUNNING_MODERATE),
        ).forEach { level ->
            val policy = policyWith("a", "b", "c")

            assertEquals("level $level", TrimResponse.SAVE_STATE, policy.onTrimMemory(level, lowMemory = false))
            assertEquals("level $level", 3, policy.capacity)
            assertEquals("level $level", emptyList<String>(), policy.evictions(keep = setOf("c")))
        }
    }

    @Test
    fun criticalLevelsDropToOneWebView() {
        listOf(trimLevel(RUNNING_CRITICAL), trimLevel(COMPLETE)).forEach { level ->
            val policy = policyWith("a", "b", "c")

            assertEquals("level $level", TrimResponse.EVICT, policy.onTrimMemory(level, lowMemory = false))
            assertEquals("level $level", 1, policy.capacity)
            assertEquals("level $level", listOf("a", "b"), policy.evictions(keep = setOf("c")))
        }
    }

    @Test
    fun lowMemoryEvictsWhateverTheLevel() {
        val policy = policyWith("a", "b", "c")

        assertEquals(TrimResponse.EVICT, policy.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, lowMemory = true))
        assertEquals(1, policy.capacity)
    }

    @Test
    fun aLevelBelowEveryKnownOneDoesNothing() {
        val policy = policyWith("a", "b", "c")

        assertEquals(TrimResponse.NONE, policy.onTrimMemory(0, lowMemory = false))
        assertEquals(3, policy.capacity)
    }

    @Test
    fun theCapacityComesBackWhenTheActivityStarts() {
        val trimmed = policyWith("a")
        trimmed.onTrimMemory(trimLevel(COMPLETE), lowMemory = false)
        trimmed.onStart()
        assertEquals(3, trimmed.capacity)

        val killed = policyWith("a")
        killed.onRendererKilled()
        assertEquals("a renderer killed for memory", 1, killed.capacity)
        killed.onStart()
        assertEquals(3, killed.capacity)
    }

    @Test
    fun theCriticalLevelsAreTheOnesTheSystemSends() {
        // The deprecated constants keep their values; a typo in the policy's copies would hide a level.
        assertEquals(15, trimLevel(RUNNING_CRITICAL))
        assertEquals(80, trimLevel(COMPLETE))
        assertEquals(20, ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(40, ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    }

    private fun policyWith(vararg tabs: String) = WebViewPoolPolicy(isLowRamDevice = false).also { policy ->
        tabs.forEach(policy::touch)
    }

    private companion object {
        const val RUNNING_MODERATE = "RUNNING_MODERATE"
        const val RUNNING_LOW = "RUNNING_LOW"
        const val RUNNING_CRITICAL = "RUNNING_CRITICAL"
        const val MODERATE = "MODERATE"
        const val COMPLETE = "COMPLETE"

        /** The deprecated trim levels, which Android before 14 still sends. */
        @Suppress("DEPRECATION")
        fun trimLevel(name: String): Int = when (name) {
            RUNNING_MODERATE -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
            RUNNING_LOW -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            RUNNING_CRITICAL -> ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            MODERATE -> ComponentCallbacks2.TRIM_MEMORY_MODERATE
            COMPLETE -> ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            else -> error(name)
        }
    }
}
