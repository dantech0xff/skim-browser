package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.ai.AiDataCleared
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import com.dantech.skimbrowser.domain.browsing.OpenPages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The bulk deletes forward one call and keep the flags they were given. */
class BulkClearUseCasesTest {

    @Test
    fun deleteAllAiDataForwards() = runTest {
        val eraser = RecordingEraser()
        assertEquals(AiDataCleared.CHATS_AND_SITE_CHOICES, DeleteAllAiData(eraser)())
        assertEquals(listOf("all"), eraser.calls)
    }

    @Test
    fun clearSkimHistoryForwardsAndLeavesSiteChoicesToTheEraser() = runTest {
        val eraser = RecordingEraser()
        assertEquals(AiDataCleared.CHATS, ClearSkimHistory(eraser)())
        assertEquals(listOf("history"), eraser.calls)
    }

    @Test
    fun clearBrowsingDataForwardsTheFlagsAndThePages() = runTest {
        val pages = object : OpenPages {
            override fun clearHistory(tabId: String) = Unit
            override fun clearCache() = Unit
        }
        var seen: PagesCall? = null
        val clear = ClearBrowsingData { history, cookies, cache, received ->
            seen = PagesCall(history, cookies, cache, received)
        }

        clear(history = true, cookies = false, cache = true, pages = pages)

        assertEquals(PagesCall(true, false, true, pages), seen)
        assertSame(pages, seen?.pages)
    }

    private data class PagesCall(
        val history: Boolean,
        val cookies: Boolean,
        val cache: Boolean,
        val pages: OpenPages?,
    )

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
