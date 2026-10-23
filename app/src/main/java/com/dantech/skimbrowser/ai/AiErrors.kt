package com.dantech.skimbrowser.ai

import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.SseException

/** Why a Skim AI request produced no answer. */
sealed interface AiFailure {
    data object Offline : AiFailure

    data object Server : AiFailure

    data object Refused : AiFailure

    /** The site is not allowed, or [hostChanged] when the page moved to another site. */
    data class NotAllowed(val hostChanged: Boolean) : AiFailure
}

/** Thrown by the one send path when a request is not allowed to leave the device. */
class AiDenied(val failure: AiFailure) : RuntimeException()

/** Groups a failure from the model client. [AiDenied] keeps the decision it already made. */
fun Throwable.toAiFailure(): AiFailure = when (this) {
    is AiDenied -> failure
    is AnthropicIoException -> AiFailure.Offline
    is RateLimitException, is InternalServerException, is SseException -> AiFailure.Server
    else -> AiFailure.Server
}
