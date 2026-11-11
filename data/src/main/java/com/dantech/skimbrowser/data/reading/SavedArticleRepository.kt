package com.dantech.skimbrowser.data.reading

import com.dantech.skimbrowser.data.db.SkimDatabase
import com.dantech.skimbrowser.data.db.dao.SavedArticleDao
import com.dantech.skimbrowser.data.db.entity.SavedArticleEntity
import com.dantech.skimbrowser.domain.page.PageBlock
import com.dantech.skimbrowser.domain.reading.SavedArticle
import com.dantech.skimbrowser.domain.reading.SavedArticleRepository as SavedArticleStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SavedArticleRepository(private val dao: SavedArticleDao) : SavedArticleStore {

    @Inject constructor(database: SkimDatabase) : this(database.savedArticleDao())

    override val articles: Flow<List<SavedArticle>> = dao.observe().map { rows -> rows.map { it.toArticle() } }

    override suspend fun save(
        url: String,
        host: String,
        title: String,
        byline: String?,
        blocks: List<PageBlock>,
    ): Long {
        dao.idOf(url)?.let { return it }
        val id = dao.insert(
            SavedArticleEntity(
                url = url,
                host = host,
                title = title,
                byline = byline,
                blocksJson = encodeBlocks(blocks),
                savedAt = System.currentTimeMillis(),
            ),
        )
        return if (id == -1L) dao.idOf(url) ?: 0L else id
    }

    override suspend fun article(id: Long): SavedArticle? = dao.get(id)?.toArticle()

    override suspend fun setSummary(id: Long, summary: String) {
        dao.setSummary(id, summary)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}

private fun SavedArticleEntity.toArticle(): SavedArticle = SavedArticle(
    id = id,
    url = url,
    host = host,
    title = title,
    byline = byline,
    blocks = decodeBlocks(blocksJson),
    savedAt = savedAt,
    summary = summary,
)

internal fun encodeBlocks(blocks: List<PageBlock>): String {
    val array = JSONArray()
    blocks.forEach { block ->
        array.put(JSONObject().put("id", block.id).put("tag", block.tag).put("text", block.text))
    }
    return array.toString()
}

internal fun decodeBlocks(json: String): List<PageBlock> = try {
    val array = JSONArray(json)
    buildList {
        for (index in 0 until array.length()) {
            val object_ = array.getJSONObject(index)
            add(PageBlock(object_.getString("id"), object_.getString("tag"), object_.getString("text")))
        }
    }
} catch (_: Exception) {
    emptyList()
}
