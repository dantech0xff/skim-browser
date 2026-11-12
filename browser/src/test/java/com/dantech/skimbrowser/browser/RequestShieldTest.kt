package com.dantech.skimbrowser.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeSettingsRepository
import com.dantech.skimbrowser.domain.shield.HostBlocklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    private companion object {
        const val PAGE = "https://www.example.com/story"
        const val OTHER = "https://news.example/story"
        const val TRACKER = "https://tracker.example/pixel"
    }
}
