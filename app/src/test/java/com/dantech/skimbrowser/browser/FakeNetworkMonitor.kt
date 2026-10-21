package com.dantech.skimbrowser.browser

import kotlinx.coroutines.flow.MutableStateFlow

/** A [NetworkMonitor] a test switches by hand. */
class FakeNetworkMonitor(validated: Boolean = true, override var hasActiveNetwork: Boolean = true) : NetworkMonitor {
    override val validated = MutableStateFlow(validated)

    fun goOffline() {
        hasActiveNetwork = false
        validated.value = false
    }

    fun comeBack() {
        hasActiveNetwork = true
        validated.value = true
    }
}
