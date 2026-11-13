package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import com.dantech.skimbrowser.domain.shield.HostBlocklist
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Stored shield exceptions apply on the first request and as soon as the switch is flipped. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RequestShieldTest {

    private val list = HostBlocklist.of(blockThird = setOf("tracker.example"))

    @Test(timeout = 5_000)
    fun aStoredExceptionIsAppliedBeforeTheFirstRequest() = runBlocking {
        val settings = FakeSettingsRepository()
        settings.setShieldDisabled("www.example.com", disabled = true)
        val shield = RequestShield.withChoices(
            list,
            settings,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        assertFalse(shield.blocks(PAGE, TRACKER, incognito = false))
        assertTrue(shield.blocks(OTHER, TRACKER, incognito = false))
    }

    @Test(timeout = 5_000)
    fun turningASiteOffIsVisibleBeforeTheStoreEmitsIt() = runTest {
        val settings = FakeSettingsRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val shield = RequestShield.withChoices(list, settings, CoroutineScope(SupervisorJob() + dispatcher))
        advanceUntilIdle()

        shield.setDisabled("example.com", incognito = false, disabled = true)

        assertFalse(shield.blocks(PAGE, TRACKER, incognito = false))
        advanceUntilIdle()
        assertTrue("example.com" in settings.shieldDisabledHosts.first())
        assertFalse(shield.blocks(PAGE, TRACKER, incognito = false))
    }

    @Test(timeout = 5_000)
    fun cosmeticRulesFollowTheSameSiteSwitch() = runBlocking {
        val cosmetic = CosmeticFilters.load("G\t.adsbox\n".reader().buffered())
        val settings = FakeSettingsRepository()
        val shield = RequestShield.withChoices(
            list,
            cosmetic,
            settings,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        shield.warmCosmetic()

        val sheet = shield.cosmeticCss(PAGE, incognito = false)
        assertTrue(sheet!!.contains(".adsbox{display:none !important}"))
        shield.setDisabled("example.com", incognito = false, disabled = true)
        assertEquals("", shield.cosmeticCss("https://www.example.com/story", incognito = false))
        assertTrue(shield.cosmeticCss(OTHER, incognito = false)!!.contains(".adsbox"))
    }

    @Test(timeout = 5_000)
    fun cosmeticCssDoesNotParseUntilWarm() {
        var parsed = false
        val shield = RequestShield.loading(
            loadCosmetic = {
                parsed = true
                CosmeticFilters.EMPTY
            },
            settings = FakeSettingsRepository(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        assertEquals(null, shield.cosmeticCss("https://example.com/", incognito = false))
        assertFalse(parsed)
    }

    @Test(timeout = 5_000)
    fun adHidingDoesNotWaitForTheCookieList() {
        var cookieParsed = false
        val shield = RequestShield.loading(
            loadCosmetic = { CosmeticFilters.load("G\t.adsbox\n".reader().buffered()) },
            loadCookie = {
                cookieParsed = true
                CosmeticFilters.EMPTY
            },
            settings = FakeSettingsRepository(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val ready = CountDownLatch(1)
        shield.whenCosmeticReady { ready.countDown() }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        assertTrue(shield.cosmeticCss(PAGE, incognito = false)!!.contains(".adsbox"))
        assertFalse(cookieParsed)
        assertEquals(null, shield.cookieCss(PAGE, incognito = false))
    }

    @Test(timeout = 5_000)
    fun turningCookieBannersOffClearsTheStylesheet() = runBlocking {
        val settings = FakeSettingsRepository()
        val shield = RequestShield.loading(
            loadCosmetic = { CosmeticFilters.EMPTY },
            loadCookie = { CosmeticFilters.load("G\t#onetrust-consent-sdk\n".reader().buffered()) },
            settings = settings,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        shield.warmCosmetic()
        assertTrue(shield.cookieCss(PAGE, incognito = false)!!.contains("onetrust"))
        var changes = 0
        shield.whenCookieChoiceChanges { changes += 1 }

        settings.setHideCookieBanners(false)

        assertEquals("", shield.cookieCss(PAGE, incognito = false))
        assertEquals(1, changes)
    }

    private companion object {
        const val PAGE = "https://www.example.com/story"
        const val OTHER = "https://news.example/story"
        const val TRACKER = "https://tracker.example/pixel"
    }
}
