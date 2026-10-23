package com.dantech.skimbrowser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import com.dantech.skimbrowser.data.settings.SettingsRepository
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.summarize(session).collect() }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.ask(session, "Key numbers").collect() }
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
