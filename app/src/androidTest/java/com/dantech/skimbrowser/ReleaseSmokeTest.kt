package com.dantech.skimbrowser

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.ui.shell.SkimAppRule
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * The minified release stand-in. The app calls `http://127.0.0.1:8787` and this test answers.
 * Run with `-Pskim.testBuildType=releaseSmoke`. It drives the UI by visible text, so renamed
 * classes do not hide a broken screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {

    private val skim = SkimAppRule()
    private val server = MockWebServer()
    private val mode = AtomicReference(Mode.SUMMARY)
    private val activityRule = ActivityScenarioRule<MainActivity>(linkIntent())
    private val compose = AndroidComposeTestRule(activityRule) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(skim)
        .around(prepare())
        .around(compose)

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun aSummaryShowsAndTheRequestCarriesNoApiKey() {
        mode.set(Mode.SUMMARY)
        ask()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("minus 25", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("From paragraph", substring = true).assertIsDisplayed()
        val request = aiRequest()
        assertEquals("/v1/messages", request.path)
        assertNull(request.getHeader("x-api-key"))
        assertNotNull(request.getHeader("X-Skim-Install"))
    }

    @Test
    fun aRefusalShowsTheRefusalCard() {
        mode.set(Mode.REFUSAL)
        ask()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Skim can't help with this", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Skim can't help with this").assertIsDisplayed()
    }

    @Test
    fun recreatingTheActivityDoesNotAskAgain() {
        mode.set(Mode.SUMMARY)
        ask()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("minus 25", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val sent = server.requestCount
        activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(sent, server.requestCount)
    }

    private fun ask() {
        compose.onNodeWithContentDescription("Ask Skim about this page").performClick()
        compose.onNodeWithText("Allow").performClick()
    }

    private fun aiRequest(): RecordedRequest {
        repeat(8) {
            val request = server.takeRequest()
            if (request.path?.startsWith("/v1/messages") == true) return request
        }
        error("no /v1/messages request")
    }

    private fun prepare(): TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                skim.setOnboarded(true)
                server.start(InetAddress.getByName(HOST), PORT)
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.path?.startsWith("/v1/messages") == true) {
                            val name = if (mode.get() == Mode.REFUSAL) "refusal.sse" else "summary-with-citations.sse"
                            return MockResponse()
                                .setHeader("Content-Type", "text/event-stream")
                                .setBody(asset(name))
                        }
                        return MockResponse()
                            .setHeader("Content-Type", "text/html; charset=utf-8")
                            .setBody(ARTICLE)
                    }
                }
                base.evaluate()
            }
        }
    }

    private fun linkIntent(): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://$HOST:$PORT/article"))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

    private enum class Mode { SUMMARY, REFUSAL }

    private companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 8787
        const val TIMEOUT = 30_000L
        val ARTICLE = """
            <!doctype html>
            <html><head><title>Why heat pumps are winning in cold climates</title></head>
            <body><article>
            <h1>Why heat pumps are winning in cold climates</h1>
            <p>For decades the advice was simple: if your winters drop well below freezing, a heat pump is not for you. That advice is now out of date, and the numbers coming out of Norway, Finland and the northern United States show how quickly it has aged.</p>
            <p>Cold-climate models keep delivering useful heat at minus 25 C. A home switching from oil saves 300 to 900 dollars a year, and the same machines qualify for the 2026 rebate in most of the states that still heat with oil.</p>
            <p>Installers in the sample compared nameplate capacity with the heat loss of the house, then checked whether the outdoor unit still met that load at the design temperature. The ones that did are the ones the article recommends.</p>
            <p>The catch is the electrical service. Several of the homes needed a panel upgrade before the heat pump could be installed, and that cost is easy to leave out of a payback that only counts the equipment.</p>
            </article></body></html>
        """.trimIndent()

        fun asset(name: String): String =
            InstrumentationRegistry.getInstrumentation().context.assets.open("sse/$name").bufferedReader().use { it.readText() }
    }
}
