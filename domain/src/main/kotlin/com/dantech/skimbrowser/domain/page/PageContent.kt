package com.dantech.skimbrowser.domain.page

import kotlinx.serialization.Serializable

/**
 * The text of a page, as Reader view (08) shows it and Skim AI reads it: the leaf blocks of the
 * article (or of the whole page), in document order. [url] is the URL the page had committed and
 * [loadNonce] the document load it was read from, so a later highlight can check that the same
 * document is still there.
 */
@Serializable
data class PageContent(
    val url: String,
    val loadNonce: Long,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val lang: String?,
    val readerable: Boolean,
    val blocks: List<PageBlock>,
)

/** One block of text; [id] is the block's `data-skim-i` number in the page, [tag] its element. */
@Serializable
data class PageBlock(val id: String, val tag: String, val text: String)

/**
 * A citation to highlight in a tab's page: block [blockId] of the [PageContent] read from
 * [expectedUrl] at [expectedNonce], or failing that the first block containing [citedText]. The
 * page must still be that document, or nothing runs.
 */
data class HighlightRequest(
    val expectedUrl: String,
    val expectedNonce: Long,
    val blockId: String,
    val citedText: String?,
)
