package com.dantech.skimbrowser.browser

import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge

/**
 * The [WebChromeClient] of one tab's WebView: load progress and title, file inputs, full-screen
 * content, windows the page opens, and the page's requests for location, camera and microphone,
 * which go to [sitePermissions].
 *
 * A file input is always answered exactly once, also when the picker is cancelled, a second input
 * replaces it, or the WebView goes away; an unanswered input would block every later one on the
 * page. A window opens only after a user gesture, as a new tab whose WebView the pool makes.
 *
 * A page's `alert`, `confirm`, `prompt` and leave-page dialogs only show for the tab on screen
 * ([isShown]), so a tab in the background cannot put a dialog over another site, and at most
 * [MAX_DIALOGS_PER_DOCUMENT] times per document ([documentId]), so a page that keeps opening them
 * cannot lock the app. Dialogs past that are answered as cancelled; a leave-page dialog is
 * answered as leave, so no page can keep the user in it. The console of an incognito page stays
 * out of the system log.
 *
 * Once [detach]ed (the pool destroys the WebView), it reports nothing more: the tab's permission
 * requests still waiting are denied, and later ones are denied at once.
 */
@MainThread
class SkimChromeClient(
    private val tabId: String,
    /** True for an incognito tab, whose site decisions live in the incognito session only. */
    private val incognito: Boolean,
    private val events: TabEventSink,
    private val activityHost: ActivityHost,
    private val fullscreen: FullscreenController,
    private val sitePermissions: SitePermissionBridge,
    /** The pool's WebView for a popup tab, not yet navigated; null when it cannot make one. */
    private val obtainForPopup: (popupTabId: String) -> WebView?,
    /** True while the tab is the current one and its WebView is shown on screen. */
    private val isShown: () -> Boolean,
    /** The tab's current document: the load nonce, new for every document it loads. */
    private val documentId: () -> Long,
    /** `FileChooserParams.parseResult`, the platform's reading of a picker result. */
    private val parseFileChooserResult: (resultCode: Int, data: Intent?) -> Array<Uri>? =
        WebChromeClient.FileChooserParams::parseResult,
) : WebChromeClient() {

    private var pendingFiles: ValueCallback<Array<Uri>>? = null
    private var detached = false
    private var dialogDocument = NO_DOCUMENT
    private var dialogsShown = 0

    /**
     * The WebView is being destroyed: a file input still waiting is answered with nothing and the
     * tab's permission requests still waiting are denied.
     */
    fun detach() {
        detached = true
        answerFiles(pendingFiles, null)
        sitePermissions.cancelTab(tabId)
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (!detached) events.onProgress(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!detached) events.onTitle(tabId, title.orEmpty())
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        // An input still waiting is answered first, so its page is never left without an answer.
        answerFiles(pendingFiles, null)
        if (detached) {
            filePathCallback.onReceiveValue(null)
            return true
        }
        val intent = try {
            fileChooserParams.createIntent()
        } catch (e: RuntimeException) {
            Log.w(TAG, "No file picker intent for this input", e)
            filePathCallback.onReceiveValue(null)
            return true
        }
        pendingFiles = filePathCallback
        activityHost.chooseFiles(intent) { resultCode, data ->
            if (pendingFiles !== filePathCallback) return@chooseFiles
            val files = try {
                parseFileChooserResult(resultCode, data)
            } catch (e: RuntimeException) {
                Log.w(TAG, "The picked files could not be read", e)
                null
            }
            answerFiles(filePathCallback, files)
        }
        return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (detached) {
            callback.onCustomViewHidden()
            return
        }
        fullscreen.enter(tabId, view, callback)
    }

    override fun onHideCustomView() {
        fullscreen.exit(tabId)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        // Pages may open windows only when the user asked for one: no pop-ups on their own.
        if (detached || !isUserGesture) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popupTabId = events.onPopupRequested(tabId) ?: return false
        val popup = obtainForPopup(popupTabId)
        if (popup == null) {
            // The tab was made for the window; without a WebView it goes again.
            events.onPopupClosed(popupTabId)
            return false
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        if (!detached) events.onPopupClosed(tabId)
    }

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean = pageDialog(result, leaving = false)

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean = pageDialog(result, leaving = false)

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean =
        pageDialog(result, leaving = false)

    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean = pageDialog(result, leaving = true)

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean =
        // True keeps the WebView from writing the message, and the page's address with it, to logcat.
        incognito || super.onConsoleMessage(consoleMessage)

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        if (detached) {
            callback.invoke(origin, false, false)
        } else {
            sitePermissions.requestLocation(tabId, incognito, origin, callback)
        }
    }

    override fun onGeolocationPermissionsHidePrompt() {
        if (!detached) sitePermissions.locationRequestWithdrawn(tabId)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        if (detached) {
            request.deny()
        } else {
            sitePermissions.requestMedia(tabId, incognito, request)
        }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (!detached) sitePermissions.mediaRequestWithdrawn(request)
    }

    /**
     * Lets the WebView show the page's dialog (false) or answers it here (true): cancelled, or for
     * a leave-page dialog ([leaving]) confirmed, when the tab is not on screen or its document
     * already showed [MAX_DIALOGS_PER_DOCUMENT] dialogs.
     */
    private fun pageDialog(result: JsResult, leaving: Boolean): Boolean {
        if (!detached && isShown()) {
            val document = documentId()
            if (document != dialogDocument) {
                dialogDocument = document
                dialogsShown = 0
            }
            if (dialogsShown < MAX_DIALOGS_PER_DOCUMENT) {
                dialogsShown++
                return false
            }
        }
        if (leaving) result.confirm() else result.cancel()
        return true
    }

    private fun answerFiles(callback: ValueCallback<Array<Uri>>?, files: Array<Uri>?) {
        if (callback == null || pendingFiles !== callback) return
        pendingFiles = null
        callback.onReceiveValue(files)
    }

    private companion object {
        const val TAG = "SkimChromeClient"

        /** Dialogs a document may show before the rest are answered without the user. */
        const val MAX_DIALOGS_PER_DOCUMENT = 3
        const val NO_DOCUMENT = Long.MIN_VALUE
    }
}
