package android.webkit

/**
 * A [WebResourceError] for JVM tests. Apps cannot make one: its constructor is hidden from the
 * SDK (package-private in android.jar, public on the device), so this class sits in its package.
 */
class TestWebResourceError(private val code: Int, private val text: CharSequence?) : WebResourceError() {
    override fun getErrorCode(): Int = code

    override fun getDescription(): CharSequence? = text
}

/**
 * A [RenderProcessGoneDetail] for JVM tests. Only the WebView makes these; the SDK marks the
 * constructor deprecated to say so, which a test standing in for the WebView has to use.
 */
@Suppress("DEPRECATION")
class TestRenderProcessGoneDetail(private val crashed: Boolean) : RenderProcessGoneDetail() {
    override fun didCrash(): Boolean = crashed

    override fun rendererPriorityAtExit(): Int = WebView.RENDERER_PRIORITY_BOUND
}

/** A [WebResourceRequest] for JVM tests, as the WebView describes a navigation or a load. */
class TestWebResourceRequest(
    private val url: String,
    private val mainFrame: Boolean,
    private val gesture: Boolean = true,
) : WebResourceRequest {
    override fun getUrl(): android.net.Uri = android.net.Uri.parse(url)

    override fun isForMainFrame(): Boolean = mainFrame

    override fun isRedirect(): Boolean = false

    override fun hasGesture(): Boolean = gesture

    override fun getMethod(): String = "GET"

    override fun getRequestHeaders(): Map<String, String> = emptyMap()
}
