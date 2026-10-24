package com.dantech.skimbrowser.ai

import com.anthropic.errors.AnthropicIoException
import com.dantech.skimbrowser.domain.ai.AiFailure

/** Thrown by the one send path when a request is not allowed to leave the device. */
class AiDenied(val failure: AiFailure) : RuntimeException()

/** Groups a failure from the model client. [AiDenied] keeps the decision it already made. */
fun Throwable.toAiFailure(): AiFailure = when (this) {
    is AiDenied -> failure
    is AnthropicIoException -> AiFailure.Offline
    else -> AiFailure.Server
}
