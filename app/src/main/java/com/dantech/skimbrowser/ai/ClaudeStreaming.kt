package com.dantech.skimbrowser.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.AsyncStreamResponse
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CitationsDelta
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import java.util.Optional
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The model's streaming reply as [AiEvent]s. Thinking is skipped. Closing the flow closes the HTTP
 * stream, which is how Stop ends a reply.
 */
fun AnthropicClient.streamEvents(params: MessageCreateParams): Flow<AiEvent> = callbackFlow {
    val accumulator = MessageAccumulator.create()
    val response = async().messages().createStreaming(params)
    response.subscribe(object : AsyncStreamResponse.Handler<RawMessageStreamEvent> {
        override fun onNext(value: RawMessageStreamEvent) {
            accumulator.accumulate(value)
            if (value.isContentBlockDelta()) {
                val delta = value.asContentBlockDelta().delta()
                when {
                    delta.isText() -> trySend(AiEvent.Text(delta.asText().text()))
                    delta.isCitations() -> cite(delta.asCitations())?.let { trySend(it) }
                }
            } else if (value.isMessageDelta()) {
                value.asMessageDelta().delta().stopReason().orElse(null)?.let { reason ->
                    trySend(AiEvent.Done(reason.asString()))
                }
            }
        }

        override fun onComplete(error: Optional<Throwable>) {
            val failure = error.orElse(null)
            if (failure != null) {
                close(failure)
            } else {
                trySend(AiEvent.Final(accumulator.message()))
                close()
            }
        }
    })
    awaitClose { response.close() }
}

private fun cite(delta: CitationsDelta): AiEvent.Cite? {
    val citation = delta.citation()
    if (!citation.isContentBlockLocation()) return null
    val location = citation.asContentBlockLocation()
    val start = location.startBlockIndex()
    val end = location.endBlockIndex()
    if (start !in 0..Int.MAX_VALUE || end !in 0..Int.MAX_VALUE) return null
    return AiEvent.Cite(start.toInt(), end.toInt(), location.citedText())
}
