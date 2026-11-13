package com.dantech.skimbrowser.browser

import android.content.Context
import android.util.Log
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.shield.CosmeticFilters
import com.dantech.skimbrowser.domain.shield.HostBlocklist
import com.dantech.skimbrowser.domain.shield.ShieldChoices
import com.dantech.skimbrowser.domain.url.hostOf
import com.dantech.skimbrowser.domain.url.normalizeHost
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The request shield. The host list is read on the first lookup, off the path that draws the first
 * frame, and then kept as hashes. Normal-tab exceptions come from [settings]. Incognito exceptions
 * live only in this process and are dropped when the incognito session ends.
 */
@Singleton
class RequestShield private constructor(
    private val loadList: () -> HostBlocklist,
    private val loadCosmetic: () -> CosmeticFilters,
    private val loadCookie: () -> CosmeticFilters,
    private val settings: SettingsRepository?,
    private val scope: CoroutineScope?,
) : ShieldChoices {

    @Inject constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        @AppScope scope: CoroutineScope,
    ) : this(
        loadList = {
            context.assets.open(ASSET).bufferedReader().use { HostBlocklist.load(it) }
        },
        loadCosmetic = {
            context.assets.open(COSMETIC_ASSET).bufferedReader().use { CosmeticFilters.load(it) }
        },
        loadCookie = {
            context.assets.open(COOKIE_ASSET).bufferedReader().use { CosmeticFilters.load(it) }
        },
        settings = settings,
        scope = scope,
    )

    private val persisted = MutableStateFlow<Set<String>>(emptySet())
    private val incognitoOff = ConcurrentHashMap.newKeySet<String>()
    /** False until the stored exceptions have been read. Blocking before that would ignore a site the user turned off. */
    private val choicesReady = AtomicBoolean(settings == null)
    /** Opens when the first snapshot is applied, or immediately when there is no store. */
    private val choicesLoaded = CountDownLatch(if (settings == null) 0 else 1)
    /**
     * The choice just published by [setDisabled], until the store reports that same set.
     * A snapshot read before the write must not put the previous choice back.
     */
    private var pinned: Set<String>? = null
    private val choiceLock = Any()
    private val gate = Any()
    private var loaded: HostBlocklist? = null
    private val cosmeticGate = Any()
    @Volatile private var cosmetic: CosmeticFilters? = null
    private val cookieGate = Any()
    @Volatile private var cookie: CosmeticFilters? = null
    @Volatile private var hideCookies = true
    private val readyWaiters = ArrayList<() -> Unit>()

    override val disabledHosts: StateFlow<Set<String>> = persisted

    init {
        if (settings != null && scope != null) {
            scope.launch {
                try {
                    settings.shieldDisabledHosts.collect { incoming ->
                        synchronized(choiceLock) { publishStored(incoming) }
                        choicesLoaded.countDown()
                    }
                } finally {
                    choicesLoaded.countDown()
                }
            }
            scope.launch {
                settings.settings.collect { hideCookies = it.hideCookieBanners }
            }
        }
    }

    override fun incognitoDisabled(host: String): Boolean = normalizeHost(host) in incognitoOff

    override suspend fun setDisabled(host: String, incognito: Boolean, disabled: Boolean) {
        val key = normalizeHost(host)
        if (key.isEmpty()) return
        if (incognito) {
            if (disabled) incognitoOff += key else incognitoOff -= key
            return
        }
        val store = settings ?: return
        // Merge with the hosts already stored. The first request waits for the same snapshot.
        if (!choicesReady.get()) choicesLoaded.await()
        // The reload that follows this call reads [persisted]. Publish the choice first, so the
        // new load does not use the previous one while the store write is still in flight.
        synchronized(choiceLock) {
            val current = persisted.value
            val next = if (disabled) current + key else current - key
            pinned = next
            persisted.value = next
            choicesReady.set(true)
        }
        choicesLoaded.countDown()
        store.setShieldDisabled(key, disabled)
    }

    /** Applies a store snapshot unless [setDisabled] has published a newer choice. */
    private fun publishStored(incoming: Set<String>) {
        val normalized = incoming.map(::normalizeHost).filter { it.isNotEmpty() }.toSet()
        val pin = pinned
        if (pin == null) {
            persisted.value = normalized
        } else if (pin == normalized) {
            pinned = null
            persisted.value = normalized
        } else {
            persisted.value = pin
        }
        choicesReady.set(true)
    }

    override fun blocks(documentUrl: String?, requestUrl: String, incognito: Boolean): Boolean {
        if (!choicesReady.get()) choicesLoaded.await()
        if (!choicesReady.get()) return false
        val requestHost = hostOf(requestUrl) ?: return false
        val documentHost = documentUrl?.let(::hostOf)
        if (documentHost != null && isOff(documentHost, incognito)) return false
        return list().blocks(documentHost, requestHost)
    }

    override fun clearIncognito() {
        incognitoOff.clear()
    }

    override fun cosmeticCss(documentUrl: String?, incognito: Boolean): String? {
        val host = documentUrl?.let(::hostOf) ?: return ""
        if (!rulesReady()) return null
        if (isOff(host, incognito)) return ""
        return cosmetic?.cssFor(host) ?: ""
    }

    override fun cookieCss(documentUrl: String?, incognito: Boolean): String? {
        if (!hideCookies) return ""
        val host = documentUrl?.let(::hostOf) ?: return ""
        return cookie?.cssFor(host)
    }

    override fun warmCosmetic() {
        if (!choicesReady.get()) choicesLoaded.await()
        cosmetics()
        cookieRules()
    }

    override fun whenCosmeticReady(action: () -> Unit) {
        if (rulesReady()) {
            action()
            return
        }
        val runner = scope
        if (runner == null) {
            warmCosmetic()
            action()
            return
        }
        val runNow = synchronized(readyWaiters) {
            if (rulesReady()) true else {
                readyWaiters += action
                false
            }
        }
        if (runNow) {
            action()
            return
        }
        runner.launch(Dispatchers.IO) {
            warmCosmetic()
            val queued = synchronized(readyWaiters) {
                if (!rulesReady()) emptyList() else readyWaiters.toList().also { readyWaiters.clear() }
            }
            queued.forEach { it() }
        }
    }

    private fun rulesReady(): Boolean = choicesReady.get() && cosmetic != null && cookie != null

    private fun isOff(host: String, incognito: Boolean): Boolean =
        if (incognito) host in incognitoOff else host in persisted.value

    private fun list(): HostBlocklist {
        loaded?.let { return it }
        synchronized(gate) {
            loaded?.let { return it }
            val parsed = try {
                loadList()
            } catch (e: Exception) {
                Log.e(TAG, "The request shield list did not load", e)
                HostBlocklist.EMPTY
            }
            loaded = parsed
            return parsed
        }
    }

    private fun cosmetics(): CosmeticFilters {
        cosmetic?.let { return it }
        synchronized(cosmeticGate) {
            cosmetic?.let { return it }
            val parsed = try {
                loadCosmetic()
            } catch (e: Exception) {
                Log.e(TAG, "The cosmetic rules did not load", e)
                CosmeticFilters.EMPTY
            }
            cosmetic = parsed
            return parsed
        }
    }

    private fun cookieRules(): CosmeticFilters {
        cookie?.let { return it }
        synchronized(cookieGate) {
            cookie?.let { return it }
            val parsed = try {
                loadCookie()
            } catch (e: Exception) {
                Log.e(TAG, "The cookie banner rules did not load", e)
                CosmeticFilters.EMPTY
            }
            cookie = parsed
            return parsed
        }
    }

    companion object {
        const val ASSET = "shield/blocklist.txt"
        const val COSMETIC_ASSET = "shield/cosmetic.txt"
        const val COOKIE_ASSET = "shield/cookie.txt"
        private const val TAG = "RequestShield"

        /** A shield over [list] with no stored choices. Unit tests build one of these. */
        fun of(list: HostBlocklist): RequestShield = of(list, CosmeticFilters.EMPTY)

        fun of(list: HostBlocklist, cosmetic: CosmeticFilters): RequestShield =
            RequestShield(
                loadList = { list },
                loadCosmetic = { cosmetic },
                loadCookie = { CosmeticFilters.EMPTY },
                settings = null,
                scope = null,
            )

        /** A shield whose normal-tab exceptions come from [settings]. Unit tests build one of these. */
        fun withChoices(
            list: HostBlocklist,
            settings: SettingsRepository,
            scope: CoroutineScope,
        ): RequestShield = withChoices(list, CosmeticFilters.EMPTY, settings, scope)

        /** A shield whose cosmetic file is read by [loadCosmetic], for tests that must not parse early. */
        fun loading(
            loadCosmetic: () -> CosmeticFilters,
            settings: SettingsRepository,
            scope: CoroutineScope,
        ): RequestShield = RequestShield(
            loadList = { HostBlocklist.EMPTY },
            loadCosmetic = loadCosmetic,
            loadCookie = { CosmeticFilters.EMPTY },
            settings = settings,
            scope = scope,
        )

        fun withChoices(
            list: HostBlocklist,
            cosmetic: CosmeticFilters,
            settings: SettingsRepository,
            scope: CoroutineScope,
        ): RequestShield = RequestShield(
            loadList = { list },
            loadCosmetic = { cosmetic },
            loadCookie = { CosmeticFilters.EMPTY },
            settings = settings,
            scope = scope,
        )
    }
}
