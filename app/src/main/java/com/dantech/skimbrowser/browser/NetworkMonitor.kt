package com.dantech.skimbrowser.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The phone's connectivity, as far as page loading cares. */
interface NetworkMonitor : NetworkAvailability {
    /** True while there is a default network at all, validated or not. Read when needed. */
    override val hasActiveNetwork: Boolean

    /** True while the default network is validated, that is, reaches the internet. */
    override val validated: StateFlow<Boolean>
}

/**
 * [NetworkMonitor] over [ConnectivityManager]'s default network callback, for the life of the
 * process. Callbacks arrive on the connectivity thread; the flow is safe to read anywhere.
 */
class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor, Closeable {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** The default network the callback reported last; capabilities of any other one are stale. */
    @Volatile
    private var defaultNetwork: Network? = null

    private val _validated = MutableStateFlow(isValidated(connectivity.activeNetwork))

    override val validated: StateFlow<Boolean> = _validated.asStateFlow()

    override val hasActiveNetwork: Boolean
        get() = connectivity.activeNetwork != null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            defaultNetwork = network
            _validated.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        override fun onLost(network: Network) {
            if (network == defaultNetwork) {
                defaultNetwork = null
                _validated.value = false
            }
        }
    }

    private val registered: Boolean = try {
        connectivity.registerDefaultNetworkCallback(callback)
        true
    } catch (e: RuntimeException) {
        // Too many callbacks in the process, or no network permission: the state stays as read once.
        Log.w(TAG, "Network callback not registered", e)
        false
    }

    /** Stops listening; [validated] keeps its last value. */
    override fun close() {
        if (registered) connectivity.unregisterNetworkCallback(callback)
    }

    private fun isValidated(network: Network?): Boolean =
        network != null && connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    private companion object {
        const val TAG = "SkimNetwork"
    }
}
