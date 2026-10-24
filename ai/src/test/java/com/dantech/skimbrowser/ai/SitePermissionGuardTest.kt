package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** A page is sent only when its site is allowed and it is still the site that was approved. */
@RunWith(AndroidJUnit4::class)
class SitePermissionGuardTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun aSiteSetToAskIsNotSent() = runBlocking {
        val (service, server, sites, settings) = rig()
        sites.setSkimAi(PAGE_HOST, SkimAiState.ASK)
        settings.setAskBeforeSending(false)
        assertDenied(service.summarize(allowedSession()))
        assertEquals(0, server.requestCount)
        server.close()
    }

    @Test
    fun anUnsetSiteIsNotSentWhileAskBeforeSendingIsOn() = runBlocking {
        val (service, server, _, settings) = rig()
        settings.setAskBeforeSending(true)
        assertDenied(service.summarize(allowedSession()))
        assertEquals(0, server.requestCount)
        server.close()
    }

    @Test
    fun aPageThatMovedHostIsNotSent() = runBlocking {
        val (service, server, sites, settings) = rig()
        sites.setSkimAi("other.example", SkimAiState.ALLOWED)
        settings.setAskBeforeSending(false)
        val failure = assertDenied(service.summarize(allowedSession(host = "other.example", approvedHost = PAGE_HOST)))
        assertTrue(failure is AiFailure.NotAllowed && failure.hostChanged)
        assertEquals(0, server.requestCount)
        server.close()
    }

    private fun rig(): Rig {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) {
                folder.newFile("settings-${databases.size}.preferences_pb")
            },
        )
        val sites = SitePermissionRepository(db.sitePermissionDao(), db)
        val server = FakeClaudeServer()
        val service = SkimAiService(ClaudeClientProvider(server.baseUrl, "test-key") { "install" }, sites, settings)
        return Rig(service, server, sites, settings)
    }

    private fun assertDenied(events: Flow<AiReply>): AiFailure {
        val failed = runBlocking { events.toList() }.filterIsInstance<AiReply.Failed>()
        assertEquals(failed.toString(), 1, failed.size)
        return failed.single().failure
    }

    private data class Rig(
        val service: SkimAiService,
        val server: FakeClaudeServer,
        val sites: SitePermissionRepository,
        val settings: SettingsRepository,
    )
}
