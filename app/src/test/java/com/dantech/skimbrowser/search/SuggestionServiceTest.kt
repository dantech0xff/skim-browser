package com.dantech.skimbrowser.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ReleaseProcessResources
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.TestStorage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import javax.inject.Provider
import com.dantech.skimbrowser.data.settings.SearchEngine
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Real HTTP: the service builds each engine's real https address, and the test client sends it to a
 * local server instead (scheme, host and port swapped, path and query kept), so what the server
 * receives is exactly what the engine would.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SuggestionServiceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val folder = TemporaryFolder()

    @Inject lateinit var resources: ReleaseProcessResources
    @Inject lateinit var appService: Provider<SuggestionService>
    @Inject lateinit var appClient: OkHttpClient

    private val server = MockWebServer()
    private val other = MockWebServer()

    /** The addresses the service asked for, before the test client swapped them. */
    private val asked = CopyOnWriteArrayList<HttpUrl>()
    private val cancelled = AtomicInteger()
    private val clients = mutableListOf<OkHttpClient>()

    @Before
    fun setUp() {
        server.start()
        other.start()
    }

    @After
    fun tearDown() {
        if (::resources.isInitialized) resources.release()
        TestStorage.current.set(null)
        clients.forEach { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
        server.shutdown()
        other.shutdown()
    }

    // The four engines

    @Test
    fun eachEngineGetsItsOwnAddressAndItsFormatGivesItsSuggestions() = runBlocking {
        val formats = mapOf(
            SearchEngine.GOOGLE to "text/javascript; charset=UTF-8",
            SearchEngine.BING to "application/json; charset=utf-8",
            SearchEngine.DUCKDUCKGO to "application/javascript; charset=UTF-8",
            SearchEngine.ECOSIA to "application/json; charset=utf-8",
        )
        val service = service()

        formats.forEach { (engine, type) ->
            val body = fixture(engine.name.lowercase())
            server.enqueue(MockResponse().setHeader("Content-Type", type).setBody(body))

            val found = service.suggestions(engine, "heat pump")

            assertEquals(engine.name, parseOpenSearchSuggestions(body), found)
            assertTrue(engine.name, found.isNotEmpty())
            assertEquals(engine.suggestUrl("heat pump").toHttpUrl(), asked.last())
            val received = server.received()
            assertEquals(engine.suggestUrl("heat pump").toHttpUrl().encodedPath, received.requestUrl!!.encodedPath)
            assertEquals(engine.suggestUrl("heat pump").toHttpUrl().encodedQuery, received.requestUrl!!.encodedQuery)
            assertEquals("GET", received.method)
        }
    }

    @Test
    fun theQueryTravelsEncodedAndComesBackExactly() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("bing")))

        service().suggestions(SearchEngine.BING, "đà nẵng & co?#/")

        assertEquals("đà nẵng & co?#/", server.received().requestUrl!!.queryParameter("query"))
    }

    @Test
    fun everyEngineAddressIsHttpsOnTheEnginesOwnHostWhateverTheQuery() {
        val queries = listOf("heat pump", "@evil.example/", "a/../../b", "x#y?z", "\\evil.example", "%2F%2Fevil", "q=1&client=evil", "đà nẵng")
        SearchEngine.entries.forEach { engine ->
            val host = engine.suggestTemplate.substringAfter("://").substringBefore('/')
            queries.forEach { query ->
                val url = checkNotNull(SuggestionService.suggestionAddress(engine, query)) { "$engine $query" }
                assertTrue(url.isHttps)
                assertEquals(host, url.host)
                val parameter = if (engine == SearchEngine.BING) "query" else "q"
                assertEquals(query, url.queryParameter(parameter))
            }
        }
    }

    // Charsets

    @Test
    fun theBodyIsReadInTheCharsetItsHeaderNames() = runBlocking {
        val latin1 = Buffer().write("""["caf""".toByteArray(Charsets.ISO_8859_1))
            .write(byteArrayOf(0xE9.toByte()))
            .write("""",["caf""".toByteArray(Charsets.ISO_8859_1))
            .write(byteArrayOf(0xE9.toByte()))
            .write(""" au lait"]]""".toByteArray(Charsets.ISO_8859_1))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/javascript; charset=ISO-8859-1").setBody(latin1))

        assertEquals(listOf("café au lait"), service().suggestions(SearchEngine.GOOGLE, "café"))
    }

    @Test
    fun withoutACharsetTheBodyIsUtf8() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""["café",["café au lait"]]"""))

        assertEquals(listOf("café au lait"), service().suggestions(SearchEngine.BING, "café"))
    }

    @Test
    fun anUnknownCharsetFallsBackToUtf8() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json; charset=no-such-charset").setBody("""["café",["café au lait"]]"""))

        assertEquals(listOf("café au lait"), service().suggestions(SearchEngine.ECOSIA, "café"))
    }

    // Failures give nothing

    @Test
    fun httpErrorsGiveNoSuggestions() = runBlocking {
        val service = service()
        listOf(400, 403, 404, 429, 500, 503).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody(fixture("google")))

            assertEquals("HTTP $code", emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "heat pump"))
        }
    }

    @Test
    fun aRedirectIsNotFollowed() = runBlocking {
        other.enqueue(MockResponse().setBody(fixture("google")))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/complete/search?q=heat").toString()))

        assertEquals(emptyList<String>(), service().suggestions(SearchEngine.GOOGLE, "heat pump"))
        assertEquals(1, server.requestCount)
        assertEquals("the other host is never asked", 0, other.requestCount)
    }

    @Test
    fun aBodyThatIsNotSuggestionsGivesNone() = runBlocking {
        val service = service()
        listOf("<html><body>Unusual traffic</body></html>", """["heat",["heat pump"""", "", """{"error":"quota"}""").forEach { body ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(body))

            assertEquals(body, emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "heat pump"))
        }
    }

    @Test
    fun aConnectionThatDropsGivesNone() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        // One attempt: a retry would wait for a second response the server never queues.
        val service = SuggestionService(client { retryOnConnectionFailure(false) })

        assertEquals(emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "heat pump"))
    }

    @Test
    fun aBodyOfExactlyTheLimitIsRead() = runBlocking {
        val body = padded("""["heat",["heat pump"]]""", SuggestionService.MAX_BODY_BYTES.toInt())
        server.enqueue(MockResponse().setBody(body))

        assertEquals(listOf("heat pump"), service().suggestions(SearchEngine.GOOGLE, "heat pump"))
    }

    @Test
    fun aBodyOverTheLimitGivesNoneWithOrWithoutItsLength() = runBlocking {
        val body = padded("""["heat",["heat pump"]]""", SuggestionService.MAX_BODY_BYTES.toInt() + 1)
        val service = service()
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setChunkedBody(body, 4096))

        assertEquals("with Content-Length", emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "heat pump"))
        assertEquals("chunked, no length", emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "heat pump"))
    }

    @Test
    fun aServerThatNeverAnswersGivesNoneOnceTheCallTimesOut() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        // Without its own call timeout nothing would end this call: the client has no read timeout.
        val service = SuggestionService(client { readTimeout(0, TimeUnit.SECONDS) }, callTimeoutMs = 500)

        val found = withTimeout(WAIT_MS) { service.suggestions(SearchEngine.GOOGLE, "heat pump") }

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun theCallTimeoutIsThreeSecondsAndRedirectsAreOffByDefault() {
        val service = SuggestionService(client())

        assertEquals(3_000, service.client.callTimeoutMillis)
        assertFalse(service.client.followRedirects)
        assertSame(CookieJar.NO_COOKIES, service.client.cookieJar)
        assertNull(service.client.cache)
    }

    // Cancellation

    @Test
    fun cancellingTheCallerCancelsTheCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val service = service()

        val lookup = async(Dispatchers.IO) { service.suggestions(SearchEngine.GOOGLE, "heat pump") }
        server.received()
        lookup.cancel()

        val failure = withTimeout(WAIT_MS) { runCatching { lookup.await() }.exceptionOrNull() }
        assertTrue(failure is CancellationException)
        assertEquals("the HTTP call itself was cancelled", 1, cancelled.get())
    }

    @Test
    fun aCancelledCallerGetsNoAnswerAtAll() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val service = service()
        var answer: List<String>? = null

        val lookup = launch(Dispatchers.IO) { answer = service.suggestions(SearchEngine.GOOGLE, "heat pump") }
        server.received()
        lookup.cancelAndJoin()

        assertNull("a cancelled lookup ends in cancellation, not in an empty list", answer)
    }

    // What is never sent

    @Test
    fun aBlankQuerySendsNothing() = runBlocking {
        val service = service()

        assertEquals(emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, ""))
        assertEquals(emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "   \t"))
        assertEquals(0, server.requestCount)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun anAddressBeingTypedOrEditedIsNotSent() = runBlocking {
        val service = service()

        listOf("example.com", "daylightjournal.c", "https://bank.example/reset?token=secret", "localhost:8080", "192.168.1.1/admin").forEach { query ->
            assertEquals(query, emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, query))
        }
        assertEquals(0, server.requestCount)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun textLongerThanTheLimitIsNotSent() = runBlocking {
        val service = service()
        server.enqueue(MockResponse().setBody(fixture("google")))

        assertEquals(emptyList<String>(), service.suggestions(SearchEngine.GOOGLE, "a ".repeat(50) + "b"))
        assertEquals(0, server.requestCount)
        assertTrue(service.suggestions(SearchEngine.GOOGLE, "a ".repeat(49) + "bb").isNotEmpty())
        assertEquals(SuggestionService.MAX_QUERY_LENGTH, server.received().requestUrl!!.queryParameter("q")!!.length)
    }

    @Test
    fun noCookieCredentialOrReferrerIsSentEvenWhenTheAppClientHasThem() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"x\""))
        server.enqueue(MockResponse().setBody(fixture("google")))
        val leaky = client {
            cookieJar(
                object : CookieJar {
                    override fun loadForRequest(url: HttpUrl): List<Cookie> =
                        listOf(Cookie.Builder().name("SID").value("secret").domain(url.host).build())

                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                },
            )
            authenticator(
                object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request =
                        response.request.newBuilder().header("Authorization", "Basic c2VjcmV0").build()
                },
            )
        }

        val found = SuggestionService(leaky).suggestions(SearchEngine.GOOGLE, "heat pump")

        assertEquals("the 401 is not answered with credentials", emptyList<String>(), found)
        assertEquals(1, server.requestCount)
        val sent = server.received()
        assertNull(sent.getHeader("Cookie"))
        assertNull(sent.getHeader("Authorization"))
        assertNull(sent.getHeader("Referer"))
    }

    @Test
    fun responsesAreNeverCachedEvenWhenTheAppClientHasACache() = runBlocking {
        repeat(2) {
            server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody(fixture("google")))
        }
        val cached = client { cache(Cache(folder.newFolder("http-cache"), 1024L * 1024)) }
        val service = SuggestionService(cached)

        service.suggestions(SearchEngine.GOOGLE, "heat pump")
        service.suggestions(SearchEngine.GOOGLE, "heat pump")

        assertEquals(2, server.requestCount)
        assertEquals(0, cached.cache!!.requestCount())
    }

    // The app's service

    @Test
    fun theAppMakesItsServiceOnFirstUseOverItsOwnClient() {
        TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        hiltRule.inject()
        val service = appService.get()

        assertSame(service, appService.get())
        assertSame(appClient.dispatcher, service.client.dispatcher)
        assertSame(appClient.connectionPool, service.client.connectionPool)
        assertEquals(3_000, service.client.callTimeoutMillis)
        assertTrue(
            "an interceptor on the shared client would add to what goes to the search engine",
            appClient.interceptors.isEmpty() && appClient.networkInterceptors.isEmpty(),
        )
    }

    /** A service whose requests reach [server] instead of the engine. */
    private fun service(): SuggestionService = SuggestionService(client())

    /**
     * The app's client as a test makes it: every request goes to [server], keeping its path and
     * query, and each cancelled call is counted.
     */
    private fun client(configure: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requested = chain.request().url
                asked += requested
                val local = requested.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(chain.request().newBuilder().url(local).build())
            }
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        cancelled.incrementAndGet()
                    }
                },
            )
            .readTimeout(10, TimeUnit.SECONDS)
            .apply(configure)
            .build()
            .also { clients += it }

    private fun MockWebServer.received(): RecordedRequest = checkNotNull(takeRequest(WAIT_MS, TimeUnit.MILLISECONDS)) { "The server got no request" }

    /** [json] followed by spaces up to [size] bytes in UTF-8: still the same JSON. */
    private fun padded(json: String, size: Int): String = json + " ".repeat(size - json.toByteArray(Charsets.UTF_8).size)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("suggest/$name.json")) { "Missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
