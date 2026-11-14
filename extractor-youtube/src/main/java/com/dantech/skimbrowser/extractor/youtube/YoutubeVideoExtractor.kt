package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.domain.video.VideoVariant

internal class YoutubeVideoExtractor(
    private val catalog: YoutubeCatalog,
) : VideoExtractor {
    override val id: String = "youtube"

    override fun matches(pageUrl: String): Boolean = YoutubeUrls.matches(pageUrl)

    override suspend fun extract(hints: PageVideoHints): List<VideoVariant> {
        if (!matches(hints.pageUrl)) return emptyList()
        return catalog.load(hints)
    }
}
