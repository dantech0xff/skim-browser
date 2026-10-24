package com.dantech.skimbrowser.ui.skimhistory

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Clear Skim history. The delete runs on [appScope], so it finishes if the dialog leaves.
 * The dialog closes after the returned job completes, on the caller's scope.
 */
@HiltViewModel
class ClearSkimHistoryViewModel @Inject constructor(
    private val eraser: SkimDataEraser,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    fun clear(): Job = appScope.launch { eraser.clearSkimHistory() }
}
