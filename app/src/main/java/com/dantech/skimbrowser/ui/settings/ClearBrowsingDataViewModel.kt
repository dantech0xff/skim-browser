package com.dantech.skimbrowser.ui.settings

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.domain.browsing.OpenPages
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.usecase.ClearBrowsingData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Clear browsing data (15). The clear runs on [appScope] so it finishes after the dialog closes.
 * [pages] is the Activity's pool for this call only.
 */
@HiltViewModel
class ClearBrowsingDataViewModel @Inject constructor(
    private val clearBrowsingData: ClearBrowsingData,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    fun clear(history: Boolean, cookies: Boolean, cache: Boolean, pages: OpenPages?) {
        appScope.launch { clearBrowsingData(history, cookies, cache, pages) }
    }
}
