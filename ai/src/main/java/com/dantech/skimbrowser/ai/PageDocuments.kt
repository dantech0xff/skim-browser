package com.dantech.skimbrowser.ai

import com.dantech.skimbrowser.browser.page.PageContent

/**
 * The page as one document the model can cite. Each block of [content] is its own text block, so a
 * citation's block index is the paragraph number. The document is marked cacheable; a short page
 * is simply not cached by the API.
 */
fun pageDocument(content: PageContent): WireBlock.Document = WireBlock.Document(
    title = content.title,
    context = content.url,
    paragraphs = content.blocks.map { it.text },
    citations = true,
    cache = true,
)

/** Shown in a tab document when that tab has no page text to send. */
internal const val NO_PAGE_TEXT = "No page text"

/** The last text block of a grouping request. It names no page. */
internal const val GROUPING_INSTRUCTION =
    "Group these tabs by topic. Each document context starts with its ref. Use only those refs."

/**
 * One tab as a document the model cannot cite. [cache] marks the last document of an across-tabs
 * answer so the API can reuse it on the next question. A grouping request leaves it off.
 */
internal fun tabDocument(title: String, context: String, text: String, cache: Boolean): WireBlock.Document =
    WireBlock.Document(
        title = title.ifBlank { context },
        context = context,
        paragraphs = listOf(text.ifBlank { NO_PAGE_TEXT }),
        citations = false,
        cache = cache,
    )
