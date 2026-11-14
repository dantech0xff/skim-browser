package com.dantech.skimbrowser.di

import com.dantech.skimbrowser.domain.video.VideoExtractor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Both flavors can inject this set. Only the full flavor adds an extractor. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VideoExtractorBindings {
    @Multibinds
    abstract fun extractors(): Set<VideoExtractor>
}
