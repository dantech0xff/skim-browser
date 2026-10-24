package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Ask Skim opens, asks, or stays off from the site's saved choice. */
@RunWith(AndroidJUnit4::class)
class AiGateTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun theDecisionFollowsIncognitoAskBeforeSendingAndTheSite() = runBlocking {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) { folder.newFile("settings.preferences_pb") },
        )
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        val gate = AiGate(sites, settings)

        assertEquals(AiGateDecision.OffInIncognito, gate.decide(tab(incognito = true)))

        settings.setAskBeforeSending(true)
        assertEquals(AiGateDecision.NeedsPermission, gate.decide(tab()))
        settings.setAskBeforeSending(false)
        assertEquals(AiGateDecision.Allowed, gate.decide(tab()))

        gate.allow(PAGE_HOST)
        settings.setAskBeforeSending(true)
        assertEquals(AiGateDecision.Allowed, gate.decide(tab()))
        assertEquals(SkimAiState.ALLOWED, sites.get(PAGE_HOST)?.skimAi)

        sites.setSkimAi(PAGE_HOST, SkimAiState.ASK)
        settings.setAskBeforeSending(false)
        assertEquals(AiGateDecision.NeedsPermission, gate.decide(tab()))
        settings.setAskBeforeSending(true)
        assertEquals(AiGateDecision.NeedsPermission, gate.decide(tab()))
    }

    private fun tab(incognito: Boolean = false) = Tab(
        id = "tab-1",
        incognito = incognito,
        url = PAGE_URL,
        title = "Heat pumps",
        groupId = null,
        position = 0,
        createdAt = 0,
        lastAccessedAt = 0,
    )
}
