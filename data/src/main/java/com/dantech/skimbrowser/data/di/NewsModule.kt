package com.dantech.skimbrowser.data.news

import android.content.Context
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.domain.news.NewsFeedClient
import com.dantech.skimbrowser.domain.news.NewsParser
import com.dantech.skimbrowser.domain.news.NewsRepository
import com.dantech.skimbrowser.domain.news.ThumbnailPolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NewsBindingsModule {
    @Binds abstract fun parser(impl: RssPullParser): NewsParser

    @Binds abstract fun client(impl: HttpNewsFeedClient): NewsFeedClient

    @Binds abstract fun thumbnails(impl: ConnectivityThumbnailPolicy): ThumbnailPolicy
}

@Module
@InstallIn(SingletonComponent::class)
internal object NewsRepositoryModule {
    @Provides
    @Singleton
    fun repository(storage: AppStorage, @ApplicationContext context: Context): NewsRepository =
        FileNewsRepository(storage.newsDir(context))
}
