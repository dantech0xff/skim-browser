package com.dantech.skimbrowser.ui.browsing

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.LocalBrowserHost
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.browser.downloads.DownloadEvent
import com.dantech.skimbrowser.browser.downloads.DownloadNames
import com.dantech.skimbrowser.browser.downloads.DownloadTracker
import com.dantech.skimbrowser.domain.text.cutTo
import com.dantech.skimbrowser.domain.text.toVisibleText
import com.dantech.skimbrowser.ui.snackbar.SnackbarAction
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reports how each download ends with the app's snackbar, on whatever screen shows (15): "<file>
 * downloaded" with Open, which opens the file with the type the system keeps for it (never the one
 * the page declared), or "Couldn't download <file>" as an error for 4 s, also for a download the
 * page was refused. Only downloads that end while this is composed are reported, each once.
 *
 * The file's name comes from the page: it shows as plain visible text, cut in its middle when it is
 * long so its extension stays in sight. A snackbar that names a file an incognito tab downloaded may
 * show over a screen that is not incognito, so the window keeps FLAG_SECURE from before it shows
 * until it has faded out after it closed (timed out, replaced, its action tapped), or until this
 * leaves the composition.
 */
@Composable
fun DownloadSnackbarEffect(
    tracker: DownloadTracker,
    snackbar: SnackbarController,
    secure: WindowPrivacy = LocalBrowserHost.current.secure,
) {
    LaunchedEffect(tracker, snackbar, secure) {
        val guard = IncognitoSnackbarGuard(secure, scope = this)
        try {
            launch { snackbar.current.collect { message -> guard.onScreen(message?.id) } }
            tracker.events.collect { event ->
                if (event.incognito) guard.hold()
                show(event, snackbar)
                if (event.incognito) guard.shown(snackbar.current.value?.id)
            }
        } finally {
            guard.release()
        }
    }
}

private fun show(event: DownloadEvent, snackbar: SnackbarController) {
    when (event) {
        is DownloadEvent.DownloadCompleted -> snackbar.show(
            UiText.Res(R.string.snackbar_downloaded, persistentListOf(shownFileName(event.fileName))),
            SnackbarAction.OpenDownload(event.uri, event.mime),
        )
        is DownloadEvent.DownloadFailed -> snackbar.show(
            UiText.Res(R.string.snackbar_download_failed, persistentListOf(shownFileName(event.fileName))),
            isError = true,
        )
    }
}

/**
 * A download's file name as a snackbar shows it: plain visible text (no control, bidi or zero-width
 * characters), the name a nameless file is saved under when there is none, and at most
 * [MAX_SHOWN_NAME] characters: a longer one keeps its start and its extension around an ellipsis.
 */
internal fun shownFileName(name: String?): String {
    val visible = name?.toVisibleText().orEmpty().ifEmpty { DownloadNames.FALLBACK_NAME }
    if (visible.length <= MAX_SHOWN_NAME) return visible
    val dot = visible.lastIndexOf('.')
    val extension = if (dot > 0 && visible.length - dot <= MAX_SHOWN_EXTENSION + 1) visible.substring(dot) else ""
    return visible.cutTo(MAX_SHOWN_NAME - extension.length - 1).trimEnd() + ELLIPSIS + extension
}

/**
 * Holds FLAG_SECURE ([OWNER]) while a snackbar about an incognito tab's download is on screen: on
 * before the snackbar shows ([hold], then [shown] with its message), off [EXIT_MS] after the message
 * left the screen ([onScreen]), when its fade out is long over. A new incognito message in the
 * meantime keeps the flag on without a gap. Called on the main thread.
 */
@MainThread
private class IncognitoSnackbarGuard(
    private val secure: WindowPrivacy,
    private val scope: CoroutineScope,
) {
    private var held = false

    /** The message about an incognito download shown last. */
    private var message: Long? = null
    private var letGo: Job? = null

    fun hold() {
        letGo?.cancel()
        letGo = null
        if (!held) {
            held = true
            secure.setSecure(OWNER, true)
        }
    }

    fun shown(id: Long?) {
        message = id
    }

    /** The snackbar on screen is now [id] (null for none). */
    fun onScreen(id: Long?) {
        if (!held || id == message || letGo != null) return
        letGo = scope.launch {
            delay(EXIT_MS)
            letGo = null
            held = false
            secure.setSecure(OWNER, false)
        }
    }

    fun release() {
        letGo?.cancel()
        letGo = null
        if (held) {
            held = false
            secure.setSecure(OWNER, false)
        }
    }

    companion object {
        /** Its own owner: the shell's owners come and go with what the screens show. */
        const val OWNER = "incognito-download-snackbar"

        /** A closed snackbar fades out in about 0.1 s (a fast effects spring); this is well past it. */
        const val EXIT_MS = 1_000L
    }
}

/** The longest file name a snackbar shows: with " downloaded" and Open, it still fits on one line. */
private const val MAX_SHOWN_NAME = 32

/** The longest extension a download's name keeps, as the downloads make names. */
private const val MAX_SHOWN_EXTENSION = 16
private const val ELLIPSIS = "\u2026"
