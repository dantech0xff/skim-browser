package com.dantech.skimbrowser.domain.news

/** How stories from several feeds become the list the wall shows. */
object NewsBoard {
    const val MAX_ARTICLES = 50
    const val TTL_MILLIS = 30L * 60L * 1000L

    /** True while [fetchedAtMillis] is still inside the half-hour window measured at [now]. */
    fun isFresh(fetchedAtMillis: Long, now: Long): Boolean =
        now >= fetchedAtMillis && now - fetchedAtMillis < TTL_MILLIS

    /**
     * Newest first. The same link, or the same title, is kept once. At most [MAX_ARTICLES].
     * A story with no date sorts after every dated story.
     */
    fun merge(batches: List<List<NewsArticle>>): List<NewsArticle> {
        val sorted = batches.asSequence()
            .flatten()
            .sortedWith(
                compareByDescending<NewsArticle> { it.publishedAtMillis ?: Long.MIN_VALUE }
                    .thenBy { titleKey(it.title) }
                    .thenBy { it.link },
            )
        val seenLinks = HashSet<String>()
        val seenTitles = HashSet<String>()
        val out = ArrayList<NewsArticle>(MAX_ARTICLES)
        for (article in sorted) {
            if (article.title.isBlank() || article.link.isBlank()) continue
            if (!seenLinks.add(linkKey(article.link))) continue
            val title = titleKey(article.title)
            if (title.isNotEmpty() && !seenTitles.add(title)) continue
            out += article
            if (out.size == MAX_ARTICLES) break
        }
        return out
    }

    fun linkKey(link: String): String = link.trim().substringBefore('#').trimEnd('/').lowercase()

    fun titleKey(title: String): String = title.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
