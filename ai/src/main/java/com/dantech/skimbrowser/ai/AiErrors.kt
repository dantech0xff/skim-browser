package com.dantech.skimbrowser.ai

import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.dantech.skimbrowser.domain.ai.AiFailure

/** Thrown by the one send path when a request is not allowed to leave the device. */
class AiDenied(val failure: AiFailure) : RuntimeException()

/** Groups a failure from the model client. [AiDenied] keeps the decision it already made. */
fun Throwable.toAiFailure(): AiFailure {
    val chain = generateSequence(this) { error -> error.cause?.takeIf { it !== error } }
    chain.filterIsInstance<AiDenied>().firstOrNull()?.let { return it.failure }
    if (chain.any { it is RateLimitException }) return AiFailure.DailyLimit
    if (chain.any { it is AnthropicIoException }) return AiFailure.Offline
    return AiFailure.Server
}
