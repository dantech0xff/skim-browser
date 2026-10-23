package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.permission.ResolveSkimAiConsent
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiAudience
import com.dantech.skimbrowser.domain.permission.SkimAiConsent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.url.hostOf
import kotlinx.coroutines.flow.first

/** What Ask Skim does with a tab before any page text is read. */
enum class AiGateDecision {
    /** Incognito never asks and never sends. */
    OffInIncognito,

    /** The site has not been allowed, so the permission dialog comes first. */
    NeedsPermission,

    /** The site is allowed. The sheet may open. */
    Allowed,
}

/**
 * The door in front of Ask Skim. [decide] reads the site's saved choice. [allow] records that
 * the person let Skim read [host].
 */
class AiGate(
    private val sites: SitePermissionRepository,
    private val settings: SettingsRepository,
    private val resolveConsent: ResolveSkimAiConsent = ResolveSkimAiConsent(),
) {
    suspend fun decide(tab: Tab): AiGateDecision {
        if (tab.incognito) return AiGateDecision.OffInIncognito
        val host = hostOf(tab.url.orEmpty()) ?: return AiGateDecision.NeedsPermission
        val state = sites.get(host)?.skimAi ?: SkimAiState.UNSET
        val askFirst = settings.settings.first().askBeforeSending
        return when (resolveConsent(state, askFirst, SkimAiAudience.PAGE)) {
            SkimAiConsent.Allowed -> AiGateDecision.Allowed
            SkimAiConsent.NeedsPermission -> AiGateDecision.NeedsPermission
        }
    }

    suspend fun allow(host: String) {
        sites.setSkimAi(host, SkimAiState.ALLOWED)
    }
}
