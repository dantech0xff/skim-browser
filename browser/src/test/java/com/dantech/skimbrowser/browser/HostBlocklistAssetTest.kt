package com.dantech.skimbrowser.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shield.HostBlocklist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The compiled EasyList and EasyPrivacy snapshot, and what a lookup costs once it is hashed. */
@RunWith(AndroidJUnit4::class)
class HostBlocklistAssetTest {

    private val list = ApplicationProvider.getApplicationContext<Context>()
        .assets.open(RequestShield.ASSET).bufferedReader().use { HostBlocklist.load(it) }

    @Test
    fun knownTrackerHostsAreBlockedAndANormalSiteIsNot() {
        assertTrue(list.blocks("example.com", "ad.doubleclick.net"))
        assertTrue(list.blocks("example.com", "google-analytics.com"))
        assertTrue(list.blocks("example.com", "ssl.google-analytics.com"))
        assertFalse(list.blocks("google-analytics.com", "google-analytics.com"))
        assertFalse(list.blocks("example.com", "example.com"))
        assertFalse(list.blocks("example.com", "autocomplete.clearbit.com"))
    }

    @Test
    fun aHundredThousandLookupsStayCheap() {
        val started = System.nanoTime()
        var blocked = 0
        repeat(100_000) {
            if (list.blocks("example.com", "google-analytics.com")) blocked++
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(blocked == 100_000)
        assertTrue("100k lookups took $elapsedMs ms", elapsedMs < 1_000)
        println("shield lookup 100k in $elapsedMs ms")
    }
}
