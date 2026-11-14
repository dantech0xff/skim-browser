package com.dantech.skimbrowser.data.news

import android.content.Context
import android.net.ConnectivityManager
import com.dantech.skimbrowser.domain.news.ThumbnailPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Story images stay off while the active network is metered. A missing service still shows them. */
class ConnectivityThumbnailPolicy @Inject constructor(
    @ApplicationContext context: Context,
) : ThumbnailPolicy {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun showThumbnails(): Boolean = try {
        connectivity == null || !connectivity.isActiveNetworkMetered
    } catch (e: SecurityException) {
        true
    }
}
