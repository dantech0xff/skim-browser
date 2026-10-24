package com.dantech.skimbrowser.ui.settings

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.domain.usecase.DeleteAllAiData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Delete all AI data (15). The delete runs on [appScope] and the dialog closes at once. */
class DeleteAiDataViewModel(
    private val deleteAllAiData: DeleteAllAiData,
    private val appScope: CoroutineScope,
) : ViewModel() {
    fun deleteAll() {
        appScope.launch { deleteAllAiData() }
    }
}
