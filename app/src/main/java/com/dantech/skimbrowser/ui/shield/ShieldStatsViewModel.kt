package com.dantech.skimbrowser.ui.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.di.WallClock
import com.dantech.skimbrowser.di.WallZone
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStats
import com.dantech.skimbrowser.domain.shield.shieldSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stats screen. [snapshot] is today, the last seven days, and all time. */
data class ShieldStatsUiState(
    val snapshot: ShieldSnapshot = shieldSnapshot(emptyList(), emptyList(), 0),
)

/**
 * Blocking stats. The screen reads [ShieldStats] and does not decide what a private tab records.
 * Reset runs on [appScope], so leaving the screen does not cancel it.
 */
@HiltViewModel
class ShieldStatsViewModel @Inject constructor(
    private val stats: ShieldStats,
    @AppScope private val appScope: CoroutineScope,
    @WallClock private val clock: () -> Long,
    @WallZone private val zone: @JvmSuppressWildcards () -> ZoneId,
) : ViewModel() {

    val state: StateFlow<ShieldStatsUiState> = stats.observe(today())
        .map { ShieldStatsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ShieldStatsUiState())

    fun reset() {
        appScope.launch { stats.reset() }
    }

    private fun today(): Long = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toEpochDay()

    private companion object {
        const val STOP_MS = 5_000L
    }
}
