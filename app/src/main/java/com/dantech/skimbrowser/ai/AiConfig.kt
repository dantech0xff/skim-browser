package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.OutputConfig

/** The one place the model and its limits are named. */
object AiConfig {
    const val MODEL_ID = "claude-haiku-5-5"
    const val MAX_TOKENS = 8192L

    /** Effort for a page summary and for questions about that page. */
    val EFFORT_PAGE: OutputConfig.Effort = OutputConfig.Effort.MEDIUM

    /** Below this many characters of page text, Skim does not ask for a summary. */
    const val NOT_ENOUGH_TEXT_CHARS = 300

    /** A Continue card shows at most this many characters of the first summary line. */
    const val SNIPPET_CHARS = 120
}
