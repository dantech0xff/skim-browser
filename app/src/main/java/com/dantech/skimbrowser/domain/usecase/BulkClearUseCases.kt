package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.ai.AiDataCleared
import com.dantech.skimbrowser.domain.ai.SkimDataEraser
import com.dantech.skimbrowser.domain.browsing.OpenPages

/** Deletes every saved chat and every site's Skim AI decision. */
class DeleteAllAiData(private val eraser: SkimDataEraser) {
    suspend operator fun invoke(): AiDataCleared = eraser.deleteAllAiData()
}

/** Deletes every saved chat and leaves site decisions untouched. */
class ClearSkimHistory(private val eraser: SkimDataEraser) {
    suspend operator fun invoke(): AiDataCleared = eraser.clearSkimHistory()
}

/**
 * Clear browsing data for the default profile. [pages] is the Activity's live pages, or null when
 * no Activity is up.
 */
class ClearBrowsingData(
    private val clear: suspend (history: Boolean, cookies: Boolean, cache: Boolean, pages: OpenPages?) -> Unit,
) {
    suspend operator fun invoke(history: Boolean, cookies: Boolean, cache: Boolean, pages: OpenPages?) {
        clear(history, cookies, cache, pages)
    }
}
