package com.dantech.skimbrowser.domain.ai

import com.dantech.skimbrowser.domain.tab.Tab

/**
 * One open tab that an across-tabs request may include. [ref] is "1".."N" in the order the tabs
 * are sent, most recently used first. [text] is the page text when it was read from a WebView that
 * was already open, cut to [OpenTabLimits.TEXT_CHARS]; null when the tab was not read.
 */
data class TabContext(
    val ref: String,
    val tab: Tab,
    val title: String,
    val host: String,
    val text: String?,
)

/** Limits shared by an across-tabs request and the model configuration. */
object OpenTabLimits {
    const val TEXT_CHARS = 4_000
    const val MAX_TABS = 40
    const val SNIPPET_CHARS = 120
}
