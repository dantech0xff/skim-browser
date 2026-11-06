package com.dantech.skimbrowser.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one model client. It is built on first use, off the main thread, and then reused.
 * Every build sends no key and names the install with `X-Skim-Install`. The proxy holds the key.
 * A blank [baseUrl] fails before any connection, so Ask Skim does not call out.
 */
class ClaudeClientProvider(
    private val baseUrl: String,
    private val installId: suspend () -> String,
) {
    private val gate = Mutex()
    private var cached: ClaudeClient? = null

    suspend fun client(): ClaudeClient = gate.withLock {
        cached ?: withContext(Dispatchers.Default) { build() }.also { cached = it }
    }

    private suspend fun build(): ClaudeClient {
        if (baseUrl.isBlank()) {
            throw IllegalStateException("The AI proxy URL is not set.")
        }
        return ClaudeClient(ClaudeClient.http(), baseUrl.trimEnd('/'), installId())
    }
}
