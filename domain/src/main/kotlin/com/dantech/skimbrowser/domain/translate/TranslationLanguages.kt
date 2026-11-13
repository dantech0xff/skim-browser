package com.dantech.skimbrowser.domain.translate

/**
 * The languages Translate page can target. A stored choice is one of [tags]. No stored choice
 * uses the phone's language when that language is in [tags], and English otherwise.
 */
object TranslationLanguages {
    val tags: List<String> = listOf(
        "en", "es", "fr", "de", "pt", "vi", "zh", "ja", "ko", "id",
        "hi", "ar", "ru", "it", "nl", "pl", "tr", "th", "uk", "sv",
    )

    fun resolve(storedTag: String?, deviceTag: String): String =
        canonical(storedTag) ?: canonical(deviceTag) ?: "en"

    /** The catalog tag for [tag], or null when it is blank or not a language this page can target. */
    fun canonical(tag: String?): String? {
        val primary = tag?.trim()?.replace('_', '-')?.substringBefore('-')?.lowercase() ?: return null
        if (primary.length !in 2..3 || primary.any { !it.isLetter() }) return null
        return primary.takeIf { it in tagSet }
    }

    private val tagSet = tags.toSet()
}
