package com.dantech.skimbrowser.domain.ai

/** What a Skim AI delete removed. Site choices stay on [CHATS]. */
enum class AiDataCleared { CHATS, CHATS_AND_SITE_CHOICES }

/**
 * Deletes Skim's saved chats. [deleteAllAiData] also forgets every site's Skim AI decision.
 * [clearSkimHistory] leaves those decisions as they are.
 */
interface SkimDataEraser {
    suspend fun deleteAllAiData(): AiDataCleared

    suspend fun clearSkimHistory(): AiDataCleared
}
