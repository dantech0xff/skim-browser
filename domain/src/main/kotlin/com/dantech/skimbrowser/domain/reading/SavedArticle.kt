package com.dantech.skimbrowser.domain.reading

import com.dantech.skimbrowser.domain.page.PageBlock
import kotlinx.coroutines.flow.Flow

/** An article kept on the phone so Reader view can open it without the network. */
data class SavedArticle(
    val id: Long,
    val url: String,
    val host: String,
    val title: String,
    val byline: String?,
    val blocks: List<PageBlock>,
    val savedAt: Long,
    /** The optional summary, kept with the article. It is not a Skim chat. */
    val summary: String?,
)

/** Saved articles. Incognito pages are never written. */
interface SavedArticleRepository {
    val articles: Flow<List<SavedArticle>>

    suspend fun save(
        url: String,
        host: String,
        title: String,
        byline: String?,
        blocks: List<PageBlock>,
    ): Long

    suspend fun article(id: Long): SavedArticle?

    suspend fun setSummary(id: Long, summary: String)

    suspend fun delete(id: Long)
}
