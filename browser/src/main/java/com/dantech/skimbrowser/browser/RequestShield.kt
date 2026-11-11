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
    private val gate = Any()
    private var loaded: HostBlocklist? = null

    override val disabledHosts: StateFlow<Set<String>> = persisted

    init {
        if (settings != null && scope != null) {
            scope.launch {
                settings.shieldDisabledHosts.collect {
                    persisted.value = it
                    choicesReady.set(true)
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
        } else {
            settings?.setShieldDisabled(key, disabled)
        }
    }

    override fun blocks(documentUrl: String?, requestUrl: String, incognito: Boolean): Boolean {
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
    }
}
