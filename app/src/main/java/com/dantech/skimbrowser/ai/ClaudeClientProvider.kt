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
 * A debug build sends [apiKey]. A release build sends no key and names the install with
 * `X-Skim-Install` instead, so the proxy can recognize the app without holding a key.
 */
class ClaudeClientProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val installId: suspend () -> String,
) {
    private val gate = Mutex()
    private var cached: AnthropicClient? = null

    suspend fun client(): AnthropicClient = gate.withLock {
        cached ?: withContext(Dispatchers.Default) { build() }.also { cached = it }
    }

    private suspend fun build(): AnthropicClient {
        val timeout = Timeout.builder()
            .connect(Duration.ofSeconds(CONNECT_SECONDS))
            .read(Duration.ofSeconds(READ_SECONDS))
            .request(Duration.ofMinutes(REQUEST_MINUTES))
            .build()
        val builder = AnthropicOkHttpClient.builder()
            .baseUrl(baseUrl)
            .timeout(timeout)
            .maxRetries(0)
        val key = apiKey?.takeIf { it.isNotBlank() }
        if (key != null) {
            builder.apiKey(key)
        } else {
            builder.putHeader(INSTALL_HEADER, installId())
        }
        return builder.build()
    }

    private companion object {
        const val INSTALL_HEADER = "X-Skim-Install"
        const val CONNECT_SECONDS = 15L
        const val READ_SECONDS = 60L
        const val REQUEST_MINUTES = 5L
    }
}
