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
