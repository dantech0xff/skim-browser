package com.dantech.skimbrowser.browser

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadErrorsTest {

    private val url = "https://nightbus-kansai.jp/timetable"

    @Test
    fun aDnsFailureWithANetworkIsUnreachable() {
        val error = loadErrorOf(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED", url, hasActiveNetwork = true)

        assertEquals(LoadError.Unreachable(url, "ERR_NAME_NOT_RESOLVED"), error)
    }

    @Test
    fun internetDisconnectedIsNoInternetEvenThoughItSharesTheDnsCode() {
        val error = loadErrorOf(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_INTERNET_DISCONNECTED", url, hasActiveNetwork = true)

        assertEquals(LoadError.NoInternet(url, "ERR_INTERNET_DISCONNECTED"), error)
    }

    @Test
    fun anyFailureWithoutANetworkIsNoInternetAndKeepsItsCode() {
        assertEquals(
            LoadError.NoInternet(url, "ERR_NAME_NOT_RESOLVED"),
            loadErrorOf(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED", url, hasActiveNetwork = false),
        )
        assertEquals(
            LoadError.NoInternet(url, "ERR_CONNECTION_TIMED_OUT"),
            loadErrorOf(WebViewClient.ERROR_TIMEOUT, "net::ERR_CONNECTION_TIMED_OUT", url, hasActiveNetwork = false),
        )
        assertEquals(
            LoadError.NoInternet(url, "ERR_INTERNET_DISCONNECTED"),
            loadErrorOf(WebViewClient.ERROR_HOST_LOOKUP, null, url, hasActiveNetwork = false),
        )
    }

    @Test
    fun aTimeoutIsTimedOut() {
        assertEquals(
            LoadError.TimedOut(url, "ERR_CONNECTION_TIMED_OUT"),
            loadErrorOf(WebViewClient.ERROR_TIMEOUT, "net::ERR_CONNECTION_TIMED_OUT", url, hasActiveNetwork = true),
        )
        assertEquals(
            LoadError.TimedOut(url, "ERR_TIMED_OUT"),
            loadErrorOf(WebViewClient.ERROR_TIMEOUT, null, url, hasActiveNetwork = true),
        )
    }

    @Test
    fun aRefusedConnectionIsUnreachable() {
        assertEquals(
            LoadError.Unreachable(url, "ERR_CONNECTION_REFUSED"),
            loadErrorOf(WebViewClient.ERROR_CONNECT, "net::ERR_CONNECTION_REFUSED", url, hasActiveNetwork = true),
        )
    }

    @Test
    fun aDescriptionWithoutAChromiumNameFallsBackToTheCodesName() {
        assertEquals(
            LoadError.Unreachable(url, "ERR_NAME_NOT_RESOLVED"),
            loadErrorOf(WebViewClient.ERROR_HOST_LOOKUP, "The host could not be found", url, hasActiveNetwork = true),
        )
        assertEquals(
            LoadError.Unreachable(url, "ERR_FAILED"),
            loadErrorOf(WebViewClient.ERROR_UNKNOWN, null, url, hasActiveNetwork = true),
        )
    }
}
