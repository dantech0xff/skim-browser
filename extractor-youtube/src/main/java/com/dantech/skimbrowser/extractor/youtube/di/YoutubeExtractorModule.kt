package com.dantech.skimbrowser.extractor.youtube.di

import com.dantech.skimbrowser.domain.video.VideoExtractor
import com.dantech.skimbrowser.extractor.youtube.NewPipeYoutubeCatalog
import com.dantech.skimbrowser.extractor.youtube.OkHttpNewPipeDownloader
import com.dantech.skimbrowser.extractor.youtube.YoutubeVideoExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object YoutubeExtractorModule {
    @Provides
    @IntoSet
    @Singleton
    fun youtube(client: OkHttpClient): VideoExtractor {
        val downloader = OkHttpNewPipeDownloader(client)
        return YoutubeVideoExtractor(NewPipeYoutubeCatalog(downloader))
    }
}
