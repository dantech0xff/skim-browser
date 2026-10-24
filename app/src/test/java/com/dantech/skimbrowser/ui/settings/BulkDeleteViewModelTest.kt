package com.dantech.skimbrowser.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.ai.AiDataCleared
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import com.dantech.skimbrowser.domain.browsing.OpenPages
import com.dantech.skimbrowser.domain.browsing.SiteDataCleaner
import com.dantech.skimbrowser.testing.MainDispatcherRule
import com.dantech.skimbrowser.ui.skimhistory.ClearSkimHistoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bulk deletes run on the scope that outlives the dialog. Clear Skim history closes after the delete. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BulkDeleteViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun clearBrowsingDataRunsOnTheAppScope() = runTest(main.testDispatcher) {
        var calls = 0
        val model = ClearBrowsingDataViewModel(
            object : SiteDataCleaner {
                override suspend fun clearSite(host: String, incognitoTab: Boolean) = Unit
                override suspend fun clearAll(
                    history: Boolean,
                    cookies: Boolean,
                    cache: Boolean,
                    pages: OpenPages?,
                ) {
                    calls += 1
                }
            },
            appScope = this,
        )
        val pages = object : OpenPages {
            override fun clearHistory(tabId: String) = Unit
            override fun clearCache() = Unit
        }

        model.clear(history = true, cookies = true, cache = false, pages = pages)
        assertEquals(0, calls)
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun deleteAllAiDataRunsOnTheAppScope() = runTest(main.testDispatcher) {
        val eraser = RecordingEraser()
        val model = DeleteAiDataViewModel(eraser, appScope = this)

        model.deleteAll()
        assertEquals(emptyList<String>(), eraser.calls)
        advanceUntilIdle()
        assertEquals(listOf("all"), eraser.calls)
    }

    @Test
    fun clearSkimHistoryFinishesOnTheAppScope() = runTest(main.testDispatcher) {
        val eraser = RecordingEraser()
        val model = ClearSkimHistoryViewModel(eraser, appScope = this)

        val deleting = model.clear()
        assertFalse(deleting.isCompleted)
        advanceUntilIdle()
        assertEquals(listOf("history"), eraser.calls)
        assertEquals(true, deleting.isCompleted)
    }

    private class RecordingEraser : SkimDataEraser {
        val calls = mutableListOf<String>()

        override suspend fun deleteAllAiData(): AiDataCleared {
            calls += "all"
            return AiDataCleared.CHATS_AND_SITE_CHOICES
        }

        override suspend fun clearSkimHistory(): AiDataCleared {
            calls += "history"
            return AiDataCleared.CHATS
        }
    }
}
