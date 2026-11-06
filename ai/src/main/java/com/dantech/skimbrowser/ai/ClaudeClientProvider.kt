package com.dantech.skimbrowser.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.Timeout
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one Claude client. It is built on first use, off the main thread, and then reused.
 * Every build sends no key and names the install with `X-Skim-Install`. The proxy holds the key.
 * A blank [baseUrl] fails before any connection, so Ask Skim does not call out.
 */
class ClaudeClientProvider(
    private val baseUrl: String,
    private val installId: suspend () -> String,
) {
    private val gate = Mutex()
    private var cached: AnthropicClient? = null

    suspend fun client(): AnthropicClient = gate.withLock {
        cached ?: withContext(Dispatchers.Default) { build() }.also { cached = it }
    }

    private suspend fun build(): AnthropicClient {
        if (baseUrl.isBlank()) {
            throw IllegalStateException("The AI proxy URL is not set.")
        }
        val timeout = Timeout.builder()
            .connect(Duration.ofSeconds(CONNECT_SECONDS))
            .read(Duration.ofSeconds(READ_SECONDS))
            .request(Duration.ofMinutes(REQUEST_MINUTES))
            .build()
        return AnthropicOkHttpClient.builder()
            .baseUrl(baseUrl)
            .timeout(timeout)
            .maxRetries(0)
            .putHeader(INSTALL_HEADER, installId())
            .build()
    }

    private companion object {
        const val INSTALL_HEADER = "X-Skim-Install"
        const val CONNECT_SECONDS = 15L
        const val READ_SECONDS = 60L
        const val REQUEST_MINUTES = 5L
    }
}
