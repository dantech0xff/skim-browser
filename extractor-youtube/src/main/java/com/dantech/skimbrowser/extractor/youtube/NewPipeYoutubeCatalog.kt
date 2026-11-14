package com.dantech.skimbrowser.extractor.youtube

import com.dantech.skimbrowser.domain.video.PageVideoHints
import com.dantech.skimbrowser.domain.video.VideoVariant
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo

internal interface YoutubeCatalog {
    suspend fun load(hints: PageVideoHints): List<VideoVariant>
}

internal class NewPipeYoutubeCatalog(
    private val downloader: Downloader,
) : YoutubeCatalog {
    override suspend fun load(hints: PageVideoHints): List<VideoVariant> = withContext(Dispatchers.IO) {
        NewPipe.init(downloader)
        val info = try {
            // getInfo(url) walks ServiceList and would keep every NewPipe service in the APK.
            StreamInfo.getInfo(YoutubeService(0), hints.pageUrl)
        } catch (error: ExtractionException) {
            throw YoutubeFailures.explain(error)
        } catch (error: IOException) {
            throw YoutubeFailures.explain(error)
        }
        YoutubeVariants.fromInfo(info, hints)
    }
}
