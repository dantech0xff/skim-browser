package com.dantech.skimbrowser.domain.permission

import org.junit.Assert.assertEquals
import org.junit.Test

/** The page rule and the across-tabs rule, for every saved choice. */
class ResolveSkimAiConsentTest {

    private val resolve = ResolveSkimAiConsent()

    @Test
    fun aPageAsksWhenTheSiteSaysAskOrHasNoChoiceWhileAskBeforeSendingIsOn() {
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.ALLOWED, askBeforeSending = true, SkimAiAudience.PAGE))
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.ALLOWED, askBeforeSending = false, SkimAiAudience.PAGE))
        assertEquals(SkimAiConsent.NeedsPermission, resolve(SkimAiState.ASK, askBeforeSending = true, SkimAiAudience.PAGE))
        assertEquals(SkimAiConsent.NeedsPermission, resolve(SkimAiState.ASK, askBeforeSending = false, SkimAiAudience.PAGE))
        assertEquals(SkimAiConsent.NeedsPermission, resolve(SkimAiState.UNSET, askBeforeSending = true, SkimAiAudience.PAGE))
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.UNSET, askBeforeSending = false, SkimAiAudience.PAGE))
    }

    @Test
    fun tabsRefuseOnlyASiteSetToAsk() {
        assertEquals(SkimAiConsent.NeedsPermission, resolve(SkimAiState.ASK, askBeforeSending = true, SkimAiAudience.TABS))
        assertEquals(SkimAiConsent.NeedsPermission, resolve(SkimAiState.ASK, askBeforeSending = false, SkimAiAudience.TABS))
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.UNSET, askBeforeSending = true, SkimAiAudience.TABS))
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.UNSET, askBeforeSending = false, SkimAiAudience.TABS))
        assertEquals(SkimAiConsent.Allowed, resolve(SkimAiState.ALLOWED, askBeforeSending = true, SkimAiAudience.TABS))
    }
}
