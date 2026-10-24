package com.dantech.skimbrowser.ui.settings

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.usecase.DeleteAllAiData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Delete all AI data (15). The delete runs on [appScope] and the dialog closes at once. */
@HiltViewModel
class DeleteAiDataViewModel @Inject constructor(
    private val deleteAllAiData: DeleteAllAiData,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    fun deleteAll() {
        appScope.launch { deleteAllAiData() }
    }
}
