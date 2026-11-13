package com.dantech.skimbrowser.domain.translate

import kotlinx.serialization.Serializable

data class PageTextNode(
    val id: Int,
    val text: String,
    val inViewport: Boolean,
)

@Serializable
data class TranslatedText(
    val id: Int,
    val text: String,
)
