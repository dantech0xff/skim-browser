package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.interrupt.InterruptPolicy
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The popup and redirect choices, readable on the main thread. The DataStore is collected off that
 * thread. Until the first snapshot arrives the defaults apply: both blocks on, no site allowed.
 */
@Singleton
class InterruptGate @Inject constructor(
    settings: SettingsRepository,
    @AppScope scope: CoroutineScope,
) {
    @Volatile private var current = InterruptPolicy()

    init {
        scope.launch {
            combine(settings.settings, settings.popupAllowHosts, settings.redirectAllowHosts) { stored, popups, redirects ->
                InterruptPolicy(
                    blockPopups = stored.blockPopups,
                    blockOddRedirects = stored.blockOddRedirects,
                    popupAllowHosts = popups,
                    redirectAllowHosts = redirects,
                )
            }.collect { current = it }
        }
    }

    fun policy(): InterruptPolicy = current
}
