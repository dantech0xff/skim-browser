package com.dantech.skimbrowser.domain.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device can reach the internet. The browser layer implements this over the
 * platform connectivity callback. Screens read it; they do not read the platform types.
 */
interface NetworkAvailability {
    /** True while there is a default network at all, validated or not. */
    val hasActiveNetwork: Boolean

    /** True while the default network is validated, that is, reaches the internet. */
    val validated: StateFlow<Boolean>
}
