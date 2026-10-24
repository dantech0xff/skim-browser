package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.tab.Tab

/** What Ask Skim does with a tab before any page text is read. */
enum class AskPageDecision {
    /** Incognito never asks and never sends. */
    OffInIncognito,

    /** The site has not been allowed, so the permission dialog comes first. */
    NeedsPermission,

    /** The site is allowed. The sheet may open. */
    Allowed,
}

/** Whether Ask Skim may open for a tab. The AI gate implements this. */
interface AskPageAccess {
    suspend fun decide(tab: Tab): AskPageDecision
}
