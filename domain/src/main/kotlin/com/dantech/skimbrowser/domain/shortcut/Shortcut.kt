package com.dantech.skimbrowser.domain.shortcut

/** A shortcut tile on Home. Its letter and shape come from [name] and [position]. */
data class Shortcut(
    val id: Long = 0,
    val name: String,
    val url: String,
    val position: Int,
)
