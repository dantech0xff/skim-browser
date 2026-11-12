package com.dantech.skimbrowser.ui.browsing

import com.dantech.skimbrowser.browser.downloads.DownloadRetry
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNoticeTest {

    @Test
    fun aFailedDownloadOffersRetry() {
        val retry = DownloadRetry("tab-1", "https://example.com/guide.pdf", null, null, "application/pdf", userInitiated = false)
        val snackbar = SnackbarController(UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)))

        showDownloadNotice(DownloadNotice.Failed("guide.pdf", incognito = false, retry = retry), snackbar)

        assertTrue(snackbar.current.value?.action is SnackbarAction.RetryDownload)
        assertTrue((snackbar.current.value?.action as SnackbarAction.RetryDownload).retry.userInitiated == false)
    }

    @Test
    fun aFailureWithNothingToRetryHasNoAction() {
        val snackbar = SnackbarController(UndoRegistry(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)))

        showDownloadNotice(DownloadNotice.Failed("guide.pdf", incognito = false), snackbar)

        assertNull(snackbar.current.value?.action)
    }
}
