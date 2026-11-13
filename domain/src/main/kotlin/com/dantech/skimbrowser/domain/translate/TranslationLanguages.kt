package com.dantech.skimbrowser.domain.translate

object TranslationLanguages {
    val tags: List<String> = listOf(
        "en", "es", "fr", "de", "pt", "vi", "zh", "ja", "ko", "id",
        "hi", "ar", "ru", "it", "nl", "pl", "tr", "th", "uk", "sv",
    )

    fun resolve(storedTag: String?, deviceTag: String): String =
        canonical(storedTag) ?: canonical(deviceTag) ?: "en"

    fun canonical(tag: String?): String? {
        val primary = tag?.trim()?.replace('_', '-')?.substringBefore('-')?.lowercase() ?: return null
        if (primary.length !in 2..3 || primary.any { !it.isLetter() }) return null
        return primary.takeIf { it in tagSet }
    }

    private val tagSet = tags.toSet()
}
