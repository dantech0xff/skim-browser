package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.domain.ai.AiFailure
import java.io.IOException

/** Groups a failure from the model client. [AiDenied] keeps the decision it already made. */
fun Throwable.toAiFailure(): AiFailure {
    val chain = generateSequence(this) { error -> error.cause?.takeIf { it !== error } }
    chain.filterIsInstance<AiDenied>().firstOrNull()?.let { return it.failure }
    if (chain.any { it is ProxyLimited }) return AiFailure.DailyLimit
    if (chain.any { it is IOException }) return AiFailure.Offline
    return AiFailure.Server
}

/** A request the service refused before opening a connection. */
class AiDenied(val failure: AiFailure) : RuntimeException()
