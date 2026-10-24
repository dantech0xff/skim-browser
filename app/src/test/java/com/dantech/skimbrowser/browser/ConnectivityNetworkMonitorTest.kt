package com.dantech.skimbrowser.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(AndroidJUnit4::class)
class ConnectivityNetworkMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val monitor = ConnectivityNetworkMonitor(context)

    @After
    fun tearDown() {
        monitor.close()
    }

    @Test
    fun itListensToTheDefaultNetworkAndStopsOnClose() {
        assertEquals(1, shadowOf(connectivity).networkCallbacks.size)

        monitor.close()

        assertTrue(shadowOf(connectivity).networkCallbacks.isEmpty())
    }

    @Test
    fun validationFollowsTheDefaultNetworksCapabilities() {
        val wifi = ShadowNetwork.newInstance(1)
        callback().onAvailable(wifi)

        callback().onCapabilitiesChanged(wifi, capabilities(validated = false))
        assertFalse(monitor.validated.value)

        callback().onCapabilitiesChanged(wifi, capabilities(validated = true))
        assertTrue(monitor.validated.value)

        callback().onLost(wifi)
        assertFalse(monitor.validated.value)
    }

    @Test
    fun losingANetworkThatIsNoLongerTheDefaultChangesNothing() {
        val wifi = ShadowNetwork.newInstance(1)
        val cellular = ShadowNetwork.newInstance(2)
        callback().onAvailable(wifi)
        callback().onCapabilitiesChanged(wifi, capabilities(validated = true))

        callback().onAvailable(cellular)
        callback().onCapabilitiesChanged(cellular, capabilities(validated = true))
        callback().onLost(wifi)

        assertTrue(monitor.validated.value)
    }

    @Test
    fun anActiveNetworkIsReadWhenAsked() {
        // Robolectric starts with a connected Wi-Fi network.
        assertTrue(monitor.hasActiveNetwork)

        shadowOf(connectivity).setActiveNetworkInfo(null)

        assertFalse(monitor.hasActiveNetwork)
    }

    private fun callback(): ConnectivityManager.NetworkCallback = shadowOf(connectivity).networkCallbacks.single()

    private fun capabilities(validated: Boolean): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { capabilities ->
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
}
