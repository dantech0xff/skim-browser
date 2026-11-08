package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.FeedbackDelivery
import com.dantech.skimbrowser.domain.ai.FeedbackReport
import com.dantech.skimbrowser.domain.ai.FeedbackSender
import com.dantech.skimbrowser.domain.chat.MessageFeedback
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Posts one rating to the same proxy as the model. The install header is the abuse gate.
 * The body does not name the install, the page, or the answer text.
 */
class ProxyFeedbackSender @Inject constructor(
    private val endpoints: ClaudeClientProvider,
) : FeedbackSender {
    private val http = ClaudeClient.http()

    override suspend fun send(report: FeedbackReport): FeedbackDelivery = withContext(Dispatchers.IO) {
        val endpoint = endpoints.endpoint() ?: return@withContext FeedbackDelivery.Failed
        val (origin, installId) = endpoint
        val body = json(report)
        val call = http.newCall(
            Request.Builder()
                .url("$origin/v1/feedback")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Skim-Install", installId)
                .post(body.toRequestBody(JSON))
                .build(),
        )
        try {
            call.execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val stored = response.body?.string()?.let { text ->
                            Json.parseToJsonElement(text).jsonObject["storedRevision"]?.jsonPrimitive?.int
                        } ?: report.revision
                        FeedbackDelivery.Accepted(stored)
                    }
                    429, in 500..599 -> FeedbackDelivery.Failed
                    else -> FeedbackDelivery.Rejected
                }
            }
        } catch (_: IOException) {
            FeedbackDelivery.Failed
        }
    }

    private fun json(report: FeedbackReport): String = buildJsonObject {
        put("answerId", report.answerId)
        put("revision", report.revision)
        put("rating", report.rating.wire())
        put("surface", report.surface.name.lowercase())
        put("appVersion", report.appVersion.take(32))
    }.toString()

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

private fun MessageFeedback.wire(): String = when (this) {
    MessageFeedback.GOOD -> "good"
    MessageFeedback.POOR -> "poor"
    MessageFeedback.NONE -> "none"
}
