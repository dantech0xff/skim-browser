package com.dantech.skimbrowser.ui.shield

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.di.WallClock
import com.dantech.skimbrowser.di.WallZone
import com.dantech.skimbrowser.domain.shield.ShieldSnapshot
import com.dantech.skimbrowser.domain.shield.ShieldStats
import com.dantech.skimbrowser.domain.shield.ShieldTotals
import com.dantech.skimbrowser.domain.shield.localEpochDays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One column of the stats table. Every field is a value the screen can read without copying. */
@Immutable
data class ShieldPeriodUi(
    val requests: Long = 0,
    val popups: Long = 0,
    val redirects: Long = 0,
    val cosmetic: Long = 0,
    val bytes: Long = 0,
)

/** One site on the top-sites list. */
@Immutable
data class ShieldHostUi(val host: String, val count: Long)

/**
 * What Blocking stats draws. The lists are immutable so Compose can skip the screen when
 * the totals have not changed.
 */
@Immutable
data class ShieldStatsUiState(
    val today: ShieldPeriodUi = ShieldPeriodUi(),
    val week: ShieldPeriodUi = ShieldPeriodUi(),
    val allTime: ShieldPeriodUi = ShieldPeriodUi(),
    val chart: ImmutableList<Long> = persistentListOf(),
    val topHosts: ImmutableList<ShieldHostUi> = persistentListOf(),
)

internal fun ShieldSnapshot.toUi(): ShieldStatsUiState = ShieldStatsUiState(
    today = today.toUi(),
    week = week.toUi(),
    allTime = allTime.toUi(),
    chart = chart.toImmutableList(),
    topHosts = topHosts.map { ShieldHostUi(it.host, it.count) }.toImmutableList(),
)

private fun ShieldTotals.toUi() = ShieldPeriodUi(requests, popups, redirects, cosmetic, bytes)

/**
 * Blocking stats. The screen reads [ShieldStats] and does not decide what a private tab records.
 * Reset runs on [appScope], so leaving the screen does not cancel it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShieldStatsViewModel @Inject constructor(
    private val stats: ShieldStats,
    @AppScope private val appScope: CoroutineScope,
    @WallClock private val clock: () -> Long,
    @WallZone private val zone: @JvmSuppressWildcards () -> ZoneId,
) : ViewModel() {

    val state: StateFlow<ShieldStatsUiState> = localEpochDays(clock, zone)
        .flatMapLatest { day -> stats.observe(day) }
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ShieldStatsUiState())

    fun reset() {
        appScope.launch { stats.reset() }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
