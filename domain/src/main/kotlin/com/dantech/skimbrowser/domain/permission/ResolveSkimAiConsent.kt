package com.dantech.skimbrowser.domain.permission

/** Which kind of Skim AI request the site's saved choice is being applied to. */
enum class SkimAiAudience {
    /** One page. A site with no saved choice asks while ask-before-sending is on. */
    PAGE,

    /** An across-tabs action. A site with no saved choice is included; only ASK is refused. */
    TABS,
}

/** Whether Skim may read a site without asking again. */
sealed interface SkimAiConsent {
    data object Allowed : SkimAiConsent

    data object NeedsPermission : SkimAiConsent
}

/**
 * The one rule for a site's saved Skim AI choice and the ask-before-sending setting.
 * Ask about a page, the site information switch, and a request that is about to be sent all use it.
 */
class ResolveSkimAiConsent {
    operator fun invoke(
        state: SkimAiState,
        askBeforeSending: Boolean,
        audience: SkimAiAudience,
    ): SkimAiConsent = when (audience) {
        SkimAiAudience.PAGE -> when {
            state == SkimAiState.ASK -> SkimAiConsent.NeedsPermission
            state == SkimAiState.UNSET && askBeforeSending -> SkimAiConsent.NeedsPermission
            else -> SkimAiConsent.Allowed
        }
        SkimAiAudience.TABS -> if (state == SkimAiState.ASK) SkimAiConsent.NeedsPermission else SkimAiConsent.Allowed
    }
}
