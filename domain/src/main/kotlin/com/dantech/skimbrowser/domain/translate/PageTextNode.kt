package com.dantech.skimbrowser.domain.translate

import kotlinx.serialization.Serializable

/**
 * One visible text node on a page. [id] is the number the page script assigned, stable until the
 * document is replaced. [text] is the visible words, with whitespace collapsed. [inViewport] is
 * true when the node was on screen at the moment it was read.
 */
data class PageTextNode(
    val id: Int,
    val text: String,
    val inViewport: Boolean,
)

/** A translation to write back onto the node with [id]. */
@Serializable
data class TranslatedText(
    val id: Int,
    val text: String,
)
