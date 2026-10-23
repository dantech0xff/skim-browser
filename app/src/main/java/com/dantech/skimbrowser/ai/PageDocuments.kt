package com.dantech.skimbrowser.ai

import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.CitationsConfigParam
import com.anthropic.models.messages.ContentBlockSource
import com.anthropic.models.messages.ContentBlockSourceContent
import com.anthropic.models.messages.DocumentBlockParam
import com.dantech.skimbrowser.browser.page.PageContent

/**
 * The page as one document the model can cite. Each block of [content] is its own text block, so a
 * citation's block index is the paragraph number. The document is marked cacheable; a short page
 * is simply not cached by the API.
 */
fun pageDocument(content: PageContent): DocumentBlockParam {
    val blocks = content.blocks.map { ContentBlockSourceContent.ofText(it.text) }
    val source = ContentBlockSource.builder().contentOfBlockSource(blocks).build()
    return DocumentBlockParam.builder()
        .source(source)
        .title(content.title)
        .context(content.url)
        .citations(CitationsConfigParam.builder().enabled(true).build())
        .cacheControl(CacheControlEphemeral.builder().build())
        .build()
}

/** Shown in a tab document when that tab has no page text to send. */
internal const val NO_PAGE_TEXT = "No page text"

/** The last text block of a grouping request. It names no page. */
internal const val GROUPING_INSTRUCTION =
    "Group these tabs by topic. Each document context starts with its ref. Use only those refs."

/**
 * One tab as a document the model cannot cite. [cache] marks the last document of an across-tabs
 * answer so the API can reuse it on the next question. A grouping request leaves it off.
 */
internal fun tabDocument(title: String, context: String, text: String, cache: Boolean): DocumentBlockParam {
    val source = ContentBlockSource.builder()
        .contentOfBlockSource(listOf(ContentBlockSourceContent.ofText(text.ifBlank { NO_PAGE_TEXT })))
        .build()
    val builder = DocumentBlockParam.builder()
        .source(source)
        .title(title.ifBlank { context })
        .context(context)
    if (cache) builder.cacheControl(CacheControlEphemeral.builder().build())
    return builder.build()
}
