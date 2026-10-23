package com.dantech.skimbrowser.browser

import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.normalizeHost
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The incognito session of the process: the WebView profile its tabs share and the site
 * permission answers given in it.
 *
 * A WebView profile that was loaded can neither be deleted nor unloaded until the process ends, so
 * each session gets a profile of its own, named `incognito-<time>`. Ending a session deletes what
 * that profile holds; profiles left by earlier processes are deleted at the next start
 * ([purgeStale]). Without `MULTI_PROFILE` there is no incognito: nothing ever falls back to the
 * default profile, whose cookies normal tabs share.
 *
 * Closing the last incognito tab makes the session [State.Ending] while that close can still be
 * undone. A new incognito tab opened then starts a new session with a new profile and makes the
 * pending close final, so the old session can never come back.
 */
@MainThread
class IncognitoSession(
    private val profiles: BrowserProfiles,
    /** Files shared from incognito tabs, `cacheDir/shared/incognito/`. */
    private val sharedDir: File,
    private val appScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface State {
        data object None : State

        data class Active(val profile: String) : State

        /** The last incognito tab was closed; [pendingToken] can still undo that. */
        data class Ending(val profile: String, val pendingToken: CloseToken) : State
    }

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    val isSupported: Boolean get() = profiles.isMultiProfileSupported

    val hasHttpCacheManager: Boolean get() = profiles.hasHttpCacheManager

    private val decisions = ConcurrentHashMap<String, Map<SitePermissionKind, PermissionState>>()
    private var lastStamp = 0L

    /**
     * The profile a new incognito tab uses: the active session's, or a new session's. In [State.Ending]
     * the pending close is made final through [finalizePending] (its Undo stops working), the old
     * session ends, and a new session with another profile starts.
     *
     * @throws IllegalStateException when incognito is not supported.
     */
    fun profileForNewTab(finalizePending: (CloseToken) -> Unit): String {
        check(isSupported) { "Incognito tabs need WebView profiles (MULTI_PROFILE)" }
        when (val current = _state.value) {
            is State.Active -> return current.profile
            is State.Ending -> {
                finalizePending(current.pendingToken)
                // Finalizing the token ends the session; end it here if the caller did not.
                if (_state.value != State.None) end(engine = null)
            }
            State.None -> Unit
        }
        return start()
    }

    /** The last incognito tab was closed with [token]. */
    fun markEnding(token: CloseToken) {
        val current = _state.value
        val profile = when (current) {
            is State.Active -> current.profile
            is State.Ending -> current.profile
            State.None -> return
        }
        _state.value = State.Ending(profile, token)
    }

    /** An incognito tab came back through Undo: the session goes on. */
    fun cancelEnding() {
        val current = _state.value
        if (current is State.Ending) _state.value = State.Active(current.profile)
    }

    /**
     * Ends the session and deletes what it left: the profile's HTTP cache (through a temporary
     * WebView of the profile, when [engine] is there), its cookies, web storage and geolocation
     * answers, the permission answers given in the session, and the files shared from it. Every
     * step runs even when an earlier one fails. Without [engine] (no Activity) the cache stays until
     * [purgeStale] deletes the whole profile at the next start.
     */
    fun end(engine: TabEngine?) {
        val profile = when (val current = _state.value) {
            is State.Active -> current.profile
            is State.Ending -> current.profile
            State.None -> return
        }
        _state.value = State.None
        decisions.clear()
        if (engine != null) {
            step("clear the cache") { engine.withTemporaryWebView(profile) { it.clearCache(true) } }
        }
        step("open the profile") { profiles.get(profile) }?.let { stores ->
            step("clear cookies") { stores.clearCookies() }
            step("clear web storage") { stores.clearWebStorage() }
            step("clear geolocation") { stores.clearGeolocationPermissions() }
        }
        deleteSharedFiles()
    }

    /**
     * Deletes the incognito profiles that earlier processes left (any but the active one) and the
     * files shared from them. Run on the main thread once the first frame is drawn. A profile that
     * cannot be deleted (one loaded in this process) is left for the next start.
     */
    fun purgeStale() {
        if (isSupported) {
            val active = activeProfile()
            step("list profiles") { profiles.names() }.orEmpty()
                .filter { it.startsWith(PROFILE_PREFIX) && it != active }
                .forEach { name -> step("delete a stale profile") { profiles.delete(name) } }
        }
        if (_state.value == State.None) deleteSharedFiles()
    }

    /**
     * Forgets the cookies and web storage this session's profile holds for [host]. Nothing happens
     * without a session. The permission answers given in the session stay: the site information
     * sheet changes those on their own.
     */
    fun clearSiteData(host: String) {
        val key = normalizeHost(host)
        if (key.isEmpty() || activeProfile() == null) return
        val profile = activeProfile() ?: return
        step("clear site data") {
            profiles.get(profile)?.let { store ->
                store.expireCookiesForHost(key)
                store.deleteOriginsForHost(key)
            }
        }
    }

    /** The answer given in this session to [host] for [kind]; UNSET when there is none. */
    @AnyThread
    fun decision(host: String, kind: SitePermissionKind): PermissionState =
        decisions[normalizeHost(host)]?.get(kind) ?: PermissionState.UNSET

    /**
     * Keeps the answer [s] of [host] for [kind] until the session ends. Ignored without a session,
     * so an answer can never carry over into the next one.
     */
    @AnyThread
    fun setDecision(host: String, kind: SitePermissionKind, s: PermissionState) {
        val key = normalizeHost(host)
        if (key.isEmpty() || _state.value == State.None) return
        decisions.compute(key) { _, old ->
            val updated = (old.orEmpty() + (kind to s)).filterValues { it != PermissionState.UNSET }
            updated.ifEmpty { null }
        }
    }

    private fun start(): String {
        val name = newProfileName()
        val profile = profiles.getOrCreate(name)
        if (profiles.hasHttpCacheManager) step("turn the cache off") { profile.disableHttpCache() }
        _state.value = State.Active(name)
        return name
    }

    /** A name no profile has had: the clock only moves forward here, and taken names are skipped. */
    private fun newProfileName(): String {
        val taken = step("list profiles") { profiles.names() }.orEmpty().toSet()
        var stamp = maxOf(clock(), lastStamp + 1)
        while ("$PROFILE_PREFIX$stamp" in taken) stamp++
        lastStamp = stamp
        return "$PROFILE_PREFIX$stamp"
    }

    private fun activeProfile(): String? = when (val current = _state.value) {
        is State.Active -> current.profile
        is State.Ending -> current.profile
        State.None -> null
    }

    private fun deleteSharedFiles() {
        appScope.launch(ioDispatcher) {
            if (!sharedDir.deleteRecursively()) Log.w(TAG, "Incognito shared files not all deleted")
        }
    }

    /** Runs one clean-up [block]; a failure is logged and returns null so the next step still runs. */
    private inline fun <T> step(what: String, block: () -> T): T? = try {
        block()
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not $what", e)
        null
    }

    private companion object {
        const val TAG = "SkimIncognito"
        const val PROFILE_PREFIX = "incognito-"
    }
}
