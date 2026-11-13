package com.dantech.skimbrowser.browser

import androidx.annotation.AnyThread
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.sameNavigation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one navigation each tab is on. Starting a navigation (typed address, link, back, forward,
 * reload, proceed, back to safety, warnings switched off) records the next generation and its
 * URL. A WebView callback applies when it names that URL. A callback for any other address is
 * from an earlier navigation and is dropped.
 *
 * A redirect of the navigation that has not produced a document yet keeps the generation and
 * takes the address the WebView is now fetching. A same-site document is that redirect when the
 * platform does not mark it as one. After the document has started, a different address is a new
 * navigation, and a same-site visit with no new document is the page moving itself.
 */
@AnyThread
@Singleton
class TabNavigations @Inject constructor() {
    private class Nav(val generation: Long, var url: String, var started: Boolean, val left: String?)

    private val lock = Any()
    private val tabs = HashMap<String, Nav>()
    /** Addresses the person refused. They are not a navigation until [begin] names them again. */
    private val refused = HashMap<String, MutableSet<String>>()
    private var next = 0L

    /**
     * A Safe Browsing warning arrived before any navigation was recorded. Leaving it has to
     * remember [url], or a late start of that address is mistaken for the next page.
     */
    fun noteThreat(tabId: String, url: String) {
        synchronized(lock) {
            if (tabs[tabId] == null) tabs[tabId] = Nav(++next, url, started = false, left = null)
        }
    }

    /** [url] is the page this navigation will show. Reports of the previous one no longer apply. */
    fun begin(tabId: String, url: String) {
        synchronized(lock) { open(tabId, url, started = false) }
    }

    /**
     * The person left [url] from a scam warning. It stays refused until a navigation is started
     * to it. While it is the page this tab is on, the navigation moves off it so a later start
     * cannot put it back.
     */
    fun refuse(tabId: String, url: String) {
        synchronized(lock) {
            refused.getOrPut(tabId) { LinkedHashSet() }.add(url)
            val nav = tabs[tabId] ?: return
            if (nav.url.isEmpty() || sameNavigation(nav.url, url)) {
                tabs[tabId] = Nav(++next, "", started = false, left = url)
            }
        }
    }

    /**
     * The in-flight navigation is now fetching [url] (an HTTP redirect). A refused address is
     * not that redirect. Once the document has started, a later request belongs to the next
     * navigation.
     */
    fun follow(tabId: String, url: String) {
        synchronized(lock) {
            if (isRefused(tabId, url)) return
            val nav = tabs[tabId] ?: return
            if (!nav.started) nav.url = url
        }
    }

    /** Warnings switched off, and this tab was not held on a threat. The current page stays. */
    fun bump(tabId: String) {
        synchronized(lock) {
            val nav = tabs[tabId] ?: return
            tabs[tabId] = Nav(++next, nav.url, nav.started, nav.left)
        }
    }

    /** The tab left the web (the New tab page). A late report of the old page does not apply. */
    fun stop(tabId: String) {
        synchronized(lock) {
            open(tabId, "", started = false)
        }
    }

    /**
     * Begins [url] unless this tab is already waiting for that exact navigation. A second call
     * from the WebView, after [begin], must not throw away the generation the tab just started.
     */
    fun prepare(tabId: String, url: String) {
        synchronized(lock) {
            val nav = tabs[tabId]
            if (nav == null || nav.started || !sameNavigation(nav.url, url)) open(tabId, url, started = false)
        }
    }

    /** A main-frame document started at [url]. False: the report is stale and must be ignored. */
    fun acceptStart(tabId: String, url: String): Boolean = synchronized(lock) {
        val nav = tabs[tabId]
        if (isRefused(tabId, url) && (nav == null || !sameNavigation(nav.url, url))) return false
        if (nav == null) {
            tabs[tabId] = Nav(++next, url, started = true, left = null)
            return true
        }
        if (sameNavigation(nav.url, url) || (!nav.started && sameSite(nav.url, url))) {
            nav.url = url
            nav.started = true
            return true
        }
        if (!nav.started) {
            // The address just left can still report a start. Any other address is the document
            // this navigation produced: a redirect, or the page that failed to open.
            if (nav.left != null && sameNavigation(nav.left, url)) return false
            if (isRefused(tabId, url)) return false
            nav.url = url
            nav.started = true
            return true
        }
        open(tabId, url, started = true)
        true
    }

    private fun open(tabId: String, url: String, started: Boolean) {
        refused[tabId]?.removeAll { sameNavigation(it, url) }
        tabs[tabId] = Nav(++next, url, started, left = tabs[tabId]?.url)
    }

    /** A visit. Same-site movement after the document started is that document, not a stale load. */
    fun acceptVisit(tabId: String, url: String): Boolean = synchronized(lock) {
        val nav = tabs[tabId] ?: return true
        if (isRefused(tabId, url)) return false
        if (sameNavigation(nav.url, url)) return true
        if (nav.started && sameSite(nav.url, url)) {
            nav.url = url
            return true
        }
        false
    }

    /** Finish, error, and a Safe Browsing hit name the current navigation, or they are stale. */
    fun matches(tabId: String, url: String): Boolean = synchronized(lock) {
        val nav = tabs[tabId] ?: return true
        sameNavigation(nav.url, url)
    }

    /** A title belongs to the document that has started, not to the page a new navigation replaced. */
    fun acceptTitle(tabId: String): Boolean = synchronized(lock) {
        tabs[tabId]?.started != false
    }

    private fun isRefused(tabId: String, url: String): Boolean =
        refused[tabId]?.any { sameNavigation(it, url) } == true

    private fun sameSite(a: String, b: String): Boolean {
        val host = hostOf(a)
        return host != null && host == hostOf(b)
    }
}
