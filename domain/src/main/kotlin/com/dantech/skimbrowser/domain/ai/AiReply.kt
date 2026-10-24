package com.dantech.skimbrowser.domain.ai

/** One step of a streaming reply, without any model-SDK type. */
sealed interface AiReply {
    data class Text(val delta: String) : AiReply

    data class Cite(val start: Int, val end: Int, val citedText: String) : AiReply

    data class Done(val stopReason: String) : AiReply

    /** The model finished a message. The assistant layer kept it for the next turn when asked. */
    data object Finished : AiReply

    data class Failed(val failure: AiFailure) : AiReply
}

/** Why a Skim AI request produced no answer. */
sealed interface AiFailure {
    data object Offline : AiFailure

    data object Server : AiFailure

    data object Refused : AiFailure

    /** The site is not allowed, or [hostChanged] when the page moved to another site. */
    data class NotAllowed(val hostChanged: Boolean) : AiFailure
}
