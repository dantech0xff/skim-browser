package com.dantech.skimbrowser.browser

import android.content.Context
import android.util.Log
import com.dantech.skimbrowser.di.AppScope
import com.dantech.skimbrowser.domain.settings.SettingsRepository
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
    private val settings: SettingsRepository?,
    scope: CoroutineScope?,
) : ShieldChoices {

    @Inject constructor(
        @ApplicationContext context: Context,
        settings: SettingsRepository,
        @AppScope scope: CoroutineScope,
    ) : this(
        loadList = {
            context.assets.open(ASSET).bufferedReader().use { HostBlocklist.load(it) }
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

    companion object {
        const val ASSET = "shield/blocklist.txt"
        private const val TAG = "RequestShield"

        /** A shield over [list] with no stored choices. Unit tests build one of these. */
        fun of(list: HostBlocklist): RequestShield = RequestShield(loadList = { list }, settings = null, scope = null)

        /** A shield whose normal-tab exceptions come from [settings]. Unit tests build one of these. */
        fun withChoices(
            list: HostBlocklist,
            settings: SettingsRepository,
            scope: CoroutineScope,
        ): RequestShield = RequestShield(loadList = { list }, settings = settings, scope = scope)
    }
}
