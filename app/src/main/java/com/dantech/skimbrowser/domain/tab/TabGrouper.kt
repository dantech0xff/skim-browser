package com.dantech.skimbrowser.domain.tab

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Groups the ungrouped normal tabs while the tabs screen is open, and offers Undo of that layout.
 * Leaving the screen cancels a request that is still running and hides the banner.
 */
interface TabGrouper {
    val banner: Flow<GroupingBannerState?>

    fun start(): Job

    suspend fun undo()

    suspend fun hideBanner()
}
