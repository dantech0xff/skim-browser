package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.Message

/** One thing a streaming reply did, in the order the model produced it. */
sealed interface AiEvent {
    /** More of the answer. Thinking never arrives here. */
    data class Text(val delta: String) : AiEvent

    /** A citation of document blocks from [start] inclusive to [end] exclusive. */
    data class Cite(val start: Int, val end: Int, val citedText: String) : AiEvent

    /** The model stopped. [stopReason] is the API value, such as `end_turn` or `refusal`. */
    data class Done(val stopReason: String) : AiEvent

    /** The assembled assistant message, for the next turn of a live conversation. */
    data class Final(val message: Message) : AiEvent
}
