package com.dantech.skimbrowser

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dantech.skimbrowser.browser.TestPages
import com.dantech.skimbrowser.browser.evaluateInWebView
import com.dantech.skimbrowser.browser.jsValue
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.page.PageScripts
import com.dantech.skimbrowser.browser.page.escapeForScript
import com.dantech.skimbrowser.browser.waitUntil
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reports what this device's WebView can do, for the device gate: the features the browser relies
 * on, the WebView package and version, the Android and SQLite versions, and whether Readability
 * runs on a real page. Each item is one `SkimGate key=value` line under the log tag `SkimGate`,
 * and the same values go out as an instrumentation status.
 *
 * It reports, it does not judge: a WebView that cannot run Readability (one too old to parse it)
 * is reported as such, with the error it gave, and the tests that need Readability skip
 * themselves there ([com.dantech.skimbrowser.browser.WebViewSupport]). It only fails when there is
 * no WebView at all.
 */
@RunWith(AndroidJUnit4::class)
class DeviceCapabilitiesReportTest {

    @Test
    fun reportsWhatThisDevicesWebViewSupports() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val report = LinkedHashMap<String, String>()
        FEATURES.forEach { feature -> report[feature] = WebViewFeature.isFeatureSupported(feature).toString() }
        val webView = WebViewCompat.getCurrentWebViewPackage(context)
        report["WEBVIEW_PACKAGE"] = webView?.packageName ?: "none"
        report["WEBVIEW_VERSION"] = webView?.versionName ?: "none"
        report["SDK_INT"] = Build.VERSION.SDK_INT.toString()
        report["SQLITE_VERSION"] = sqliteVersion()

        val readability = readabilityOnARealPage(PageScripts(context.assets), context.assets)
        report["READABILITY_TYPEOF"] = readability.typeOf
        readability.parseError?.let { report["READABILITY_PARSE_ERROR"] = it }
        report["READABILITY_READERABLE"] = readability.readerable

        val status = Bundle()
        report.forEach { (key, value) ->
            Log.i(TAG, "$TAG $key=$value")
            status.putString("$TAG.$key", value)
        }
        instrumentation.sendStatus(IN_PROGRESS_STATUS, status)

        assertNotNull("the device has a WebView", webView)
    }

    /**
     * What the article fixture makes of the app's Readability, as the app's scripts run it:
     * `typeof Readability` once the library ran ("null" when the WebView could not even run the
     * script), the error the WebView gives when it cannot parse the library, and what
     * `isProbablyReaderable(document)` answers.
     */
    private class ReadabilityReport(val typeOf: String, val parseError: String?, val readerable: String)

    /** The version of the SQLite that Android's own database classes, and so Room, use here. */
    private fun sqliteVersion(): String = SQLiteDatabase.create(null).use { db ->
        db.rawQuery("SELECT sqlite_version()", null).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "unknown" }
    }

    // Only the fixture's own scripts and the probes run in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private fun readabilityOnARealPage(scripts: PageScripts, assets: AssetManager): ReadabilityReport =
        TestPages().start().use { pages ->
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                var finished = false
                lateinit var view: WebView
                scenario.onActivity { activity ->
                    view = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                finished = true
                            }
                        }
                    }
                    activity.setContentView(view, ViewGroup.LayoutParams(MATCH, MATCH))
                    view.loadUrl(pages.url("/article.html"))
                }
                try {
                    waitUntil("the article to load") { onMain { finished } }
                    val library = listOf(LIBRARY_READERABLE, LIBRARY_READABILITY).joinToString(";\n") { path ->
                        assets.open(path).bufferedReader().use { it.readText() }
                    }
                    val typeOf = jsValue(evaluateInWebView(view, "(function () {\nvar module;\n$library;\nreturn typeof Readability;\n})()"))
                    // A script the WebView cannot parse never runs, so parse the library on its own to learn why.
                    val parsed = jsValue(
                        evaluateInWebView(
                            view,
                            "(function () { try { new Function(${escapeForScript(JSONObject.quote(library))}); return null; } " +
                                "catch (e) { return String(e); } })()",
                        ),
                    )
                    val readerable = evaluateInWebView(view, scripts.readerableScript())
                    ReadabilityReport(typeOf.toString(), parsed?.toString(), readerable.toString())
                } finally {
                    onMain { view.destroy() }
                }
            }
        }

    private companion object {
        const val TAG = "SkimGate"

        /** The status code instrumentation results use for values reported while a test runs. */
        const val IN_PROGRESS_STATUS = 2
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val LIBRARY_READERABLE = "js/readability/Readability-readerable.js"
        const val LIBRARY_READABILITY = "js/readability/Readability.js"

        val FEATURES = listOf(
            WebViewFeature.MULTI_PROFILE,
            WebViewFeature.HTTP_CACHE_MANAGER,
            WebViewFeature.SAVE_STATE,
            WebViewFeature.USER_AGENT_METADATA,
            WebViewFeature.GET_WEB_VIEW_RENDERER,
            WebViewFeature.WEB_VIEW_RENDERER_TERMINATE,
            WebViewFeature.WEB_MESSAGE_LISTENER,
            WebViewFeature.DOCUMENT_START_SCRIPT,
            WebViewFeature.DELETE_BROWSING_DATA,
        )
    }
}
