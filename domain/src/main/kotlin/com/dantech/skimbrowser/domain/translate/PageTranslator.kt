package com.dantech.skimbrowser.domain.translate

import com.dantech.skimbrowser.domain.ai.AiAssistant
import com.dantech.skimbrowser.domain.ai.AiFailure
import com.dantech.skimbrowser.domain.ai.AiReply
import com.dantech.skimbrowser.domain.ai.NetworkAvailability
import com.dantech.skimbrowser.domain.permission.SitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.url.hostOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PageTranslator(
    private val tabs: TabsController,
    private val assistant: AiAssistant,
    private val settings: SettingsRepository,
    private val network: NetworkAvailability,
    private val sites: SitePermissionRepository,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow<Map<String, PageTranslation>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()
    private val tokens = mutableMapOf<String, Int>()
    private val nonces = mutableMapOf<String, Long>()

    val sessions: StateFlow<Map<String, PageTranslation>> = current

    fun translate(tabId: String, deviceLanguageTag: String) {
        val token = bump(tabId)
        jobs.remove(tabId)?.cancel()
        jobs[tabId] = scope.launch { run(tabId, deviceLanguageTag, token) }
    }

    fun showOriginal(tabId: String, original: Boolean) {
        if (current.value[tabId] !is PageTranslation.Ready) return
        val token = tokens[tabId] ?: return
        scope.launch {
            if (tokens[tabId] != token) return@launch
            if (tabs.showOriginalPageText(tabId, original) && tokens[tabId] == token) {
                val live = current.value[tabId] as? PageTranslation.Ready ?: return@launch
                set(tabId, live.copy(showingOriginal = original))
            }
        }
    }

    fun dismiss(tabId: String) {
        val token = bump(tabId)
        jobs.remove(tabId)?.cancel()
        scope.launch {
            tabs.showOriginalPageText(tabId, true)
            publish(tabId, token, PageTranslation.Idle)
        }
    }

    fun allow(tabId: String, host: String, deviceLanguageTag: String) {
        scope.launch {
            sites.setSkimAi(host, SkimAiState.ALLOWED)
            translate(tabId, deviceLanguageTag)
        }
    }

    fun onDocument(tabId: String, loadNonce: Long) {
        val previous = nonces.put(tabId, loadNonce)
        if (previous != null && previous != loadNonce) {
            bump(tabId)
            jobs.remove(tabId)?.cancel()
            set(tabId, PageTranslation.Idle)
        }
    }

    private suspend fun run(tabId: String, deviceLanguageTag: String, token: Int) {
        val tab = tabs.state.value.tab(tabId) ?: return publish(tabId, token, PageTranslation.Idle)
        val host = tab.url?.let(::hostOf).orEmpty()
        if (tab.incognito) return publish(tabId, token, PageTranslation.Failed(PageTranslation.Note.Incognito, host))
        if (host.isEmpty()) return publish(tabId, token, PageTranslation.Failed(PageTranslation.Note.Empty, host))
        if (!network.validated.value) return publish(tabId, token, PageTranslation.Failed(PageTranslation.Note.Offline, host))
        val nonce = tabs.runtime(tabId).value.loadNonce
        nonces[tabId] = nonce
        publish(tabId, token, PageTranslation.Translating(done = 0, total = 0))
        val nodes = tabs.collectPageText(tabId)
        if (moved(tabId, nonce)) return publish(tabId, token, PageTranslation.Idle)
        val chunks = TranslationChunks.plan(nodes)
        if (chunks.isEmpty()) return publish(tabId, token, PageTranslation.Failed(PageTranslation.Note.Empty, host))
        val language = TranslationLanguages.resolve(settings.settings.first().translationLanguageTag, deviceLanguageTag)
        val limited = chunks.take(TranslationChunks.MAX_CHUNKS)
        val total = limited.sumOf { it.size }
        var done = 0
        var applied = 0
        var note: PageTranslation.Note? = if (limited.size < chunks.size) PageTranslation.Note.Incomplete else null
        for (chunk in limited) {
            if (moved(tabId, nonce) || tokens[tabId] != token) return publish(tabId, token, PageTranslation.Idle)
            if (!network.validated.value) {
                note = PageTranslation.Note.Offline
                break
            }
            publish(tabId, token, PageTranslation.Translating(done, total))
            val reply = StringBuilder()
            var failure: AiFailure? = null
            try {
                assistant.translateNodes(host, language, chunk, tab.incognito).collect { event ->
                    when (event) {
                        is AiReply.Text -> reply.append(event.delta)
                        is AiReply.Failed -> failure = event.failure
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
            if (moved(tabId, nonce) || tokens[tabId] != token) return publish(tabId, token, PageTranslation.Idle)
            val stopped = failure
            if (stopped != null) {
                note = noteOf(stopped)
                break
            }
            val parsed = TranslationExchange.parseReply(reply.toString(), chunk.map { it.id }.toSet())
            if (parsed.isEmpty()) {
                note = PageTranslation.Note.Unavailable
                break
            }
            val rows = parsed.map { (id, text) -> TranslatedText(id, text) }
            if (!tabs.replacePageText(tabId, rows)) {
                note = PageTranslation.Note.Unavailable
                break
            }
            applied += rows.size
            done += chunk.size
        }
        if (tokens[tabId] != token) return
        if (applied == 0) {
            publish(tabId, token, PageTranslation.Failed(note ?: PageTranslation.Note.Unavailable, host))
        } else {
            publish(tabId, token, PageTranslation.Ready(showingOriginal = false, note = note))
        }
    }

    private fun moved(tabId: String, nonce: Long): Boolean = tabs.runtime(tabId).value.loadNonce != nonce

    private fun noteOf(failure: AiFailure): PageTranslation.Note = when (failure) {
        AiFailure.Offline -> PageTranslation.Note.Offline
        AiFailure.DailyLimit -> PageTranslation.Note.Limit
        is AiFailure.NotAllowed -> PageTranslation.Note.NotAllowed
        AiFailure.Server, AiFailure.Refused -> PageTranslation.Note.Unavailable
    }

    private fun bump(tabId: String): Int {
        val next = (tokens[tabId] ?: 0) + 1
        tokens[tabId] = next
        return next
    }

    private fun publish(tabId: String, token: Int, state: PageTranslation) {
        if (tokens[tabId] != token) return
        set(tabId, state)
    }

    private fun set(tabId: String, state: PageTranslation) {
        current.update { map -> if (state is PageTranslation.Idle) map - tabId else map + (tabId to state) }
    }
}
