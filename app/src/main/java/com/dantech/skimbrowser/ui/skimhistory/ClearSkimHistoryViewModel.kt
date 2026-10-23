package com.dantech.skimbrowser.ui.skimhistory

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.domain.usecase.ClearSkimHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Clear Skim history. The delete runs on [appScope], so it finishes if the dialog leaves.
 * The dialog closes after the returned job completes, on the caller's scope.
 */
class ClearSkimHistoryViewModel(
    private val clearSkimHistory: ClearSkimHistory,
    private val appScope: CoroutineScope,
) : ViewModel() {
    fun clear(): Job = appScope.launch { clearSkimHistory() }
}
