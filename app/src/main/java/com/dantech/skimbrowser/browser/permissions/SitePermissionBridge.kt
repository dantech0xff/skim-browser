package com.dantech.skimbrowser.browser.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.dantech.skimbrowser.browser.ActivityHost
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.TabsState
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.hostOf
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A site permission request waiting for the user: the page of tab [tabId], whose site is [host]
 * (lowercase, no "www."), asks for [kinds], the kinds that site has no decision for yet.
 */
data class PendingSitePermission(
    val tabId: String,
    val host: String,
    val kinds: Set<SitePermissionKind>,
    val incognito: Boolean,
)

/**
 * Takes the pages' requests for location, camera and microphone to Skim's site permission dialog
 * and then to Android's runtime permissions. Lives as long as the Activity and holds nothing but
 * the requests still waiting, with their WebView callbacks; every call is made on the main thread.
 *
 * A request is answered from the site's decision when it has one: BLOCKED denies it at once,
 * ALLOWED grants it once Android lets Skim use the hardware (Android's prompt comes first when
 * Skim lacks the runtime permission, and a refusal there denies the request). Without a decision
 * the request waits for the user as [pending], who answers with [allow], [block] or [dismiss].
 * Decisions of normal tabs are kept in [SitePermissionRepository]; those of incognito tabs only in
 * the [IncognitoSession], gone when it ends, and never in the repository. ALLOWED is only kept once
 * Android granted the runtime permission.
 *
 * Decisions belong to the site of the tab's own page, whichever frame of it asks: a site embedded
 * in another page never gets the decisions made on its own pages, and the dialog names the page
 * the user sees. A tab without a web page of its own gets nothing, and location needs both that
 * page and the frame that asks to be https.
 *
 * The user is only asked about the shown tab's requests, one at a time and oldest first; requests
 * of other tabs wait until their tab is shown. An answer only counts for the request the dialog
 * showed ([allow], [block], [dismiss] take it), so a page cannot slip another request under the
 * user's finger. A WebView only takes a request whole, so one that also asks for anything but the
 * camera and the microphone is denied at once.
 *
 * Every request is answered exactly once: granted, denied, or dropped when the WebView withdrew it
 * itself. Requests of a tab whose WebView goes away are denied ([cancelTab]), and so is every
 * request still waiting when the Activity is destroyed ([destroy]).
 */
@MainThread
class SitePermissionBridge(
    private val context: Context,
    /** The tabs (`TabManager.state`): only the shown tab's requests are put to the user. */
    private val tabs: StateFlow<TabsState>,
    private val repository: SitePermissionRepository,
    private val incognitoSession: IncognitoSession,
    private val activityHost: ActivityHost,
    /** Where decisions are read and written: the process's scope, so a decision is kept after the Activity. */
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {

    private sealed interface Answer {
        fun grant()

        fun deny()

        /** `retain` is false: decisions are Skim's to keep, never the WebView's. */
        class Location(val origin: String, val callback: GeolocationPermissions.Callback) : Answer {
            override fun grant() = callback.invoke(origin, true, false)

            override fun deny() = callback.invoke(origin, false, false)
        }

        class Media(val request: PermissionRequest) : Answer {
            override fun grant() = request.grant(request.resources)

            override fun deny() = request.deny()
        }
    }

    private enum class Stage {
        /** The site's decisions are being read. */
        READING,

        /** Waiting for the user's answer. */
        ASKING,

        /** Allowed: granted as soon as Android lets Skim use the hardware. */
        GRANTING,
    }

    private enum class Outcome { GRANT, DENY, WITHDRAWN }

    private class Request(
        val tabId: String,
        val host: String,
        val incognito: Boolean,
        val kinds: Set<SitePermissionKind>,
        val answer: Answer,
    ) {
        var stage = Stage.READING

        /** The kinds the site has no decision for; the user's answer decides them. */
        var undecided: Set<SitePermissionKind> = kinds

        /** Keep ALLOWED for [undecided] once the request is granted. */
        var remember = false
        var done = false
    }

    private val _pending = MutableStateFlow<PendingSitePermission?>(null)

    /** The shown tab's oldest request that waits for the user; null while there is none. */
    val pending: StateFlow<PendingSitePermission?> = _pending.asStateFlow()

    private val requests = ArrayList<Request>()
    private var shown: Request? = null

    /** The request whose runtime permission prompt is up; only one prompt shows at a time. */
    private var prompting: Request? = null
    private var destroyed = false

    private val shownTab: Job = scope.launch(mainDispatcher) {
        tabs.map { it.currentTabId }.distinctUntilChanged().collect { advance() }
    }

    /** Allows [request], when it is still the [pending] one; with [remember] the site keeps the decision. */
    fun allow(request: PendingSitePermission, remember: Boolean) {
        val shownRequest = shownFor(request) ?: return
        shownRequest.remember = remember
        shownRequest.stage = Stage.GRANTING
        shown = null
        advance()
    }

    /** Blocks [request], when it is still the [pending] one; the site keeps the decision. */
    fun block(request: PendingSitePermission) {
        val shownRequest = shownFor(request) ?: return
        store(shownRequest, shownRequest.undecided, PermissionState.BLOCKED)
        settle(shownRequest, Outcome.DENY)
        advance()
    }

    /** Denies [request] this time only (the dialog was closed), when it is still the [pending] one; nothing is kept. */
    fun dismiss(request: PendingSitePermission) {
        val shownRequest = shownFor(request) ?: return
        settle(shownRequest, Outcome.DENY)
        advance()
    }

    /**
     * `onGeolocationPermissionsShowPrompt` of tab [tabId]'s WebView for a frame of [origin]: only
     * an https frame (with a host) of an https page gets location.
     */
    internal fun requestLocation(tabId: String, incognito: Boolean, origin: String, callback: GeolocationPermissions.Callback) {
        val page = pageOf(tabId)
        val secureFrame = isHttps(origin) && hostOf(origin) != null
        val host = if (secureFrame && page != null && isHttps(page)) hostOf(page) else null
        submit(tabId, host, incognito, setOf(SitePermissionKind.LOCATION), Answer.Location(origin, callback))
    }

    /** `onGeolocationPermissionsHidePrompt`: the WebView withdrew its location request, which needs no answer. */
    internal fun locationRequestWithdrawn(tabId: String) {
        requests.firstOrNull { it.tabId == tabId && it.answer is Answer.Location }?.let { request ->
            settle(request, Outcome.WITHDRAWN)
            advance()
        }
    }

    /** `onPermissionRequest` of tab [tabId]'s WebView: only the camera and the microphone can be granted. */
    internal fun requestMedia(tabId: String, incognito: Boolean, request: PermissionRequest) {
        val resources = request.resources.orEmpty()
        val onlyMedia = resources.isNotEmpty() && resources.all { mediaKindOf(it) != null }
        val webFrame = hostOf(request.origin?.toString().orEmpty()) != null
        val host = if (onlyMedia && webFrame) pageOf(tabId)?.let(::hostOf) else null
        submit(tabId, host, incognito, resources.mapNotNull(::mediaKindOf).toSet(), Answer.Media(request))
    }

    /** `onPermissionRequestCanceled`: the WebView withdrew [request], which needs no answer. */
    internal fun mediaRequestWithdrawn(request: PermissionRequest) {
        requests.firstOrNull { (it.answer as? Answer.Media)?.request === request }?.let { waiting ->
            settle(waiting, Outcome.WITHDRAWN)
            advance()
        }
    }

    /** Tab [tabId]'s WebView goes away: its requests still waiting are denied. */
    internal fun cancelTab(tabId: String) {
        val waiting = requests.filter { it.tabId == tabId }
        if (waiting.isEmpty()) return
        waiting.forEach { settle(it, Outcome.DENY) }
        advance()
    }

    /** The Activity is being destroyed: every request still waiting is denied; later ones are denied at once. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        shownTab.cancel()
        val waiting = requests.toList()
        requests.clear()
        shown = null
        prompting = null
        _pending.value = null
        waiting.forEach { request ->
            request.done = true
            answer(request.answer, Outcome.DENY)
        }
    }

    /** The address of tab [tabId]'s own page, the top-level one; null when it has none. */
    private fun pageOf(tabId: String): String? = tabs.value.tab(tabId)?.url

    /** The request behind [request] when it is the one the dialog shows. */
    private fun shownFor(request: PendingSitePermission): Request? = shown?.takeIf { request == _pending.value }

    private fun submit(tabId: String, host: String?, incognito: Boolean, kinds: Set<SitePermissionKind>, answer: Answer) {
        if (destroyed || host == null || kinds.isEmpty()) {
            answer(answer, Outcome.DENY)
            return
        }
        val request = Request(tabId, host, incognito, kinds, answer)
        requests += request
        if (incognito) {
            decide(request, kinds.associateWith { incognitoSession.decision(host, it) })
            return
        }
        scope.launch(mainDispatcher) {
            val stored = try {
                repository.get(host)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Site decisions could not be read: ${e.javaClass.simpleName}")
                null
            }
            decide(request, kinds.associateWith { stored?.permission(it) ?: PermissionState.UNSET })
        }
    }

    private fun decide(request: Request, states: Map<SitePermissionKind, PermissionState>) {
        if (request.done) return
        when {
            PermissionState.BLOCKED in states.values -> settle(request, Outcome.DENY)
            states.values.all { it == PermissionState.ALLOWED } -> request.stage = Stage.GRANTING
            else -> {
                request.undecided = states.filterValues { it == PermissionState.UNSET }.keys
                request.stage = Stage.ASKING
            }
        }
        advance()
    }

    /**
     * Grants what Android already allows, then shows the shown tab's oldest request: its dialog
     * as [pending], or Android's prompt when it was allowed and Skim lacks the runtime permission.
     */
    private fun advance() {
        if (destroyed) return
        while (true) {
            val ready = requests.firstOrNull { it.stage == Stage.GRANTING && it !== prompting && missingPermissions(it.kinds).isEmpty() } ?: break
            grant(ready)
        }
        val currentTabId = tabs.value.currentTabId
        val next = if (prompting == null) requests.firstOrNull { it.tabId == currentTabId && it.stage != Stage.READING } else null
        shown = next?.takeIf { it.stage == Stage.ASKING }
        _pending.value = shown?.let { PendingSitePermission(it.tabId, it.host, it.undecided, it.incognito) }
        if (next != null && next.stage == Stage.GRANTING) prompt(next)
    }

    private fun prompt(request: Request) {
        prompting = request
        activityHost.requestPermissions(missingPermissions(request.kinds).toTypedArray()) { granted ->
            if (prompting === request) prompting = null
            if (!request.done) {
                if (request.kinds.all { kind -> runtimePermissionsOf(kind).any { granted[it] == true || holds(it) } }) {
                    grant(request)
                } else {
                    settle(request, Outcome.DENY)
                }
            }
            advance()
        }
    }

    private fun grant(request: Request) {
        if (request.remember) store(request, request.undecided, PermissionState.ALLOWED)
        settle(request, Outcome.GRANT)
    }

    /** Keeps [state] for [kinds] of the request's site, and answers the site's other requests waiting for the user. */
    private fun store(request: Request, kinds: Set<SitePermissionKind>, state: PermissionState) {
        if (request.incognito) {
            kinds.forEach { incognitoSession.setDecision(request.host, it, state) }
        } else {
            scope.launch {
                kinds.forEach { kind ->
                    try {
                        repository.setPermission(request.host, kind, state)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "A site decision could not be kept: ${e.javaClass.simpleName}")
                    }
                }
            }
        }
        requests.filter { it !== request && it.stage == Stage.ASKING && it.host == request.host && it.incognito == request.incognito }
            .forEach { other ->
                if (state == PermissionState.BLOCKED && other.undecided.any { it in kinds }) {
                    settle(other, Outcome.DENY)
                } else if (state == PermissionState.ALLOWED) {
                    other.undecided -= kinds
                    if (other.undecided.isEmpty()) other.stage = Stage.GRANTING
                }
            }
    }

    /** Answers [request] once and forgets it. */
    private fun settle(request: Request, outcome: Outcome) {
        if (request.done) return
        request.done = true
        requests.remove(request)
        if (shown === request) {
            shown = null
            _pending.value = null
        }
        answer(request.answer, outcome)
    }

    private fun answer(answer: Answer, outcome: Outcome) {
        try {
            when (outcome) {
                Outcome.GRANT -> answer.grant()
                Outcome.DENY -> answer.deny()
                Outcome.WITHDRAWN -> Unit
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "A site permission request could not be answered: ${e.javaClass.simpleName}")
        }
    }

    /** The runtime permissions missing for [kinds]: for location, both precise and approximate. */
    private fun missingPermissions(kinds: Set<SitePermissionKind>): List<String> =
        kinds.filter { kind -> runtimePermissionsOf(kind).none(::holds) }.flatMap(::runtimePermissionsOf)

    private fun holds(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "SkimSitePermissions"
        const val HTTPS = "https:"

        fun isHttps(url: String): Boolean = url.trim().startsWith(HTTPS, ignoreCase = true)

        /** The runtime permissions of which any one lets a site have [kind]. */
        fun runtimePermissionsOf(kind: SitePermissionKind): List<String> = when (kind) {
            SitePermissionKind.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            SitePermissionKind.CAMERA -> listOf(Manifest.permission.CAMERA)
            SitePermissionKind.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        }

        fun mediaKindOf(resource: String): SitePermissionKind? = when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermissionKind.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermissionKind.MICROPHONE
            else -> null
        }
    }
}
