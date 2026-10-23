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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** An incognito page never opens a connection. */
@RunWith(AndroidJUnit4::class)
class IncognitoGuardTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val databases = mutableListOf<SkimDatabase>()

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun summarizeAndAskRefuseAnIncognitoSource() = runBlocking {
        FakeClaudeServer().use { server ->
            val service = service(server.baseUrl)
            val session = allowedSession(incognito = true)
            val summary = service.summarize(session).toList()
            val question = service.ask(session, "Key numbers").toList()
            assertTrue(summary.single() is AiReply.Failed)
            assertTrue(question.single() is AiReply.Failed)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun overviewAndGroupingRefuseAnIncognitoTabAndAnAskSite() = runBlocking {
        FakeClaudeServer().use { server ->
            val db = inMemorySkimDatabase().also { databases += it }
            val settings = SettingsRepository(
                SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) {
                    folder.newFile("guard-settings.preferences_pb")
                },
            )
            val sites = SitePermissionRepository(db.sitePermissionDao(), db)
            val service = SkimAiService(
                ClaudeClientProvider(server.baseUrl, "test-key") { "install" },
                sites,
                settings,
            )
            val hidden = tabContext("1", "secret.example", incognito = true)
            val hiddenOverview = service.overviewTabs(allowedSession(), listOf(hidden), "Summarize").toList()
            assertTrue(hiddenOverview.single() is AiReply.Failed)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.groupTabs(listOf(hidden)) }
            }
            sites.setSkimAi(PAGE_HOST, SkimAiState.ASK)
            val asked = tabContext("1", PAGE_HOST)
            val askedOverview = service.overviewTabs(allowedSession(), listOf(asked), "Summarize").toList()
            val denied = askedOverview.filterIsInstance<AiReply.Failed>().single().failure
            assertTrue(denied is AiFailure.NotAllowed && !denied.hostChanged)
            assertThrows(AiDenied::class.java) {
                runBlocking { service.groupTabs(listOf(asked)) }
            }
            assertEquals(0, server.requestCount)
        }
    }

    private fun service(baseUrl: String): SkimAiService {
        val db = inMemorySkimDatabase().also { databases += it }
        val settings = SettingsRepository(
            SettingsRepository.createDataStore(CoroutineScope(Dispatchers.IO)) {
                folder.newFile("settings.preferences_pb")
            },
        )
        return SkimAiService(
            ClaudeClientProvider(baseUrl, "test-key") { "install" },
            SitePermissionRepository(db.sitePermissionDao(), db),
            settings,
        )
    }
}
