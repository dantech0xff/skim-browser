package com.dantech.skimbrowser.browser

import android.content.Context
import com.dantech.skimbrowser.browser.downloads.BlobBridge
import com.dantech.skimbrowser.browser.downloads.DownloadRouter
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.browser.downloads.FakeBlobPage
import com.dantech.skimbrowser.browser.downloads.FakeSystemDownloads
import com.dantech.skimbrowser.browser.downloads.InProcessDownloader
import com.dantech.skimbrowser.browser.downloads.MediaStoreDownloads
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.data.repo.SitePermissionRepository
import kotlinx.coroutines.test.TestScope
import okhttp3.OkHttpClient

/**
 * The Activity-scoped parts of the browser host besides the pool, built the way the app builds
 * them over [f]'s tab manager, with fakes only where Android and Chromium are: DownloadManager
 * ([systemDownloads]), the page that answers blob reads ([blobPage]), the default cookie store
 * ([defaultCookies]) and, when a test installs one, MediaStore. Everything runs on [f]'s main
 * dispatcher, on the test's clock.
 */
internal class TestBrowserParts(
    f: TabManagerFixture,
    scope: TestScope,
    context: Context,
    activityHost: ActivityHost,
    httpClient: OkHttpClient = OkHttpClient(),
    /** The blob script the page runs; the fake page answers whatever the test makes it answer. */
    blobScript: String = "function (args) { return null; }",
) {
    val systemDownloads = FakeSystemDownloads()
    val tracker = DownloadTracker(systemDownloads, scope.backgroundScope, f.main)
    val blobPage = FakeBlobPage(blobScript)
    private var nonces = 0
    val blob = BlobBridge(blobPage, readScript = { blobScript }, newNonce = { "nonce-${++nonces}" }, mainDispatcher = f.main)
    val store = MediaStoreDownloads(context, ioDispatcher = f.main)
    val defaultCookies = mutableMapOf<String, String>()

    /** Every URL whose default-profile cookies the downloads asked for. */
    val defaultCookieLookups = mutableListOf<String>()
    val repository = SitePermissionRepository(f.db.sitePermissionDao(), f.db)
    val sitePermissions = SitePermissionBridge(
        context = context,
        tabs = f.manager.state,
        repository = repository,
        incognitoSession = f.incognito,
        activityHost = activityHost,
        scope = scope.backgroundScope,
        mainDispatcher = f.main,
    )
    val downloads = DownloadRouter(
        tabs = f.manager,
        incognito = f.incognito,
        profiles = f.profiles,
        tracker = tracker,
        // Robolectric's file system reports no free space at all.
        inProcess = InProcessDownloader({ httpClient }, store, f.main, freeSpace = { Long.MAX_VALUE }),
        store = store,
        blob = blob,
        activityHost = activityHost,
        scope = scope.backgroundScope,
        defaultCookies = { url ->
            defaultCookieLookups += url
            defaultCookies[url]
        },
        mainDispatcher = f.main,
        ioDispatcher = f.main,
    )
}
