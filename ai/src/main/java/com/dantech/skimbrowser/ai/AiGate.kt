package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.AskPageAccess
import com.dantech.skimbrowser.domain.permission.ResolveSkimAiConsent
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiAudience
import com.dantech.skimbrowser.domain.permission.SkimAiConsent
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.Tab
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.usecase.SetSkimAi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

typealias AiGateDecision = com.dantech.skimbrowser.domain.ai.AskPageDecision

/**
 * The door in front of Ask Skim. [decide] reads the site's saved choice. [allow] records that
 * the person let Skim read [host].
 */
@Singleton
class AiGate(
    private val sites: SitePermissionRepository,
    private val settings: SettingsRepository,
    private val setSkimAi: SetSkimAi = SetSkimAi(sites),
    private val resolveConsent: ResolveSkimAiConsent = ResolveSkimAiConsent(),
) : AskPageAccess {

    @Inject constructor(sites: SitePermissionRepository, settings: SettingsRepository) :
        this(sites, settings, SetSkimAi(sites))

    override suspend fun decide(tab: Tab): AiGateDecision {
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
        setSkimAi(host, SkimAiState.ALLOWED)
    }
}
