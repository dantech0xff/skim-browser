package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.OutputConfig

/** The one place the model and its limits are named. */
object AiConfig {
    const val MODEL_ID = "claude-haiku-5-5"
    const val MAX_TOKENS = 8192L

    /** Effort for a page summary and for questions about that page. */
    val EFFORT_PAGE: OutputConfig.Effort = OutputConfig.Effort.MEDIUM

    /** Effort for an answer across open tabs. */
    val EFFORT_TABS: OutputConfig.Effort = OutputConfig.Effort.MEDIUM

    /** Effort for grouping tabs from their titles and hosts. */
    val EFFORT_GROUPING: OutputConfig.Effort = OutputConfig.Effort.LOW

    const val GROUPING_MAX_TOKENS = 2048L

    /** The longest page text sent for one tab in an across-tabs answer. */
    const val TAB_TEXT_CHARS = com.dantech.skimbrowser.domain.ai.OpenTabLimits.TEXT_CHARS

    /** The most tabs one across-tabs request includes. */
    const val MAX_TABS = com.dantech.skimbrowser.domain.ai.OpenTabLimits.MAX_TABS

    /** Below this many characters of page text, Skim does not ask for a summary. */
    const val NOT_ENOUGH_TEXT_CHARS = 300

    /** A Continue card shows at most this many characters of the first summary line. */
    const val SNIPPET_CHARS = com.dantech.skimbrowser.domain.ai.OpenTabLimits.SNIPPET_CHARS
}
