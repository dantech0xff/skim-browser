package com.dantech.skimbrowser.browser.page

import android.content.Context
import android.content.res.AssetManager
import androidx.webkit.WebViewCompat
import java.io.InputStream

/**
 * The page scripts, read from the app's assets once, on first use, and kept.
 *
 * Readability declares `function Readability` and `function isProbablyReaderable` at the top level
 * of a script, so the extract and readerable scripts run them inside one function scope: nothing
 * they declare reaches the page's global object. That scope also declares an empty `module`, so
 * Readability's CommonJS export never writes into a `module` object the page may define.
 */
class PageScripts internal constructor(
    private val open: (path: String) -> InputStream,
    /** Whether this WebView can run the scripts, asked once, on first use. */
    supported: () -> Boolean = { true },
) : PageScriptSource {

    constructor(assets: AssetManager, supported: () -> Boolean = { true }) : this(assets::open, supported)

    override val isSupported: Boolean by lazy(supported)

    private val readerableLibrary by lazy { read(READERABLE_LIBRARY) }
    private val extract by lazy { scoped(listOf(readerableLibrary, read(READABILITY)), read(EXTRACT)) }
    private val readerable by lazy { scoped(listOf(readerableLibrary), read(READERABLE)) }
    private val highlight by lazy { read(HIGHLIGHT) }

    override fun extractScript(): String = extract

    override fun readerableScript(): String = readerable

    /** The highlight function called with [argsJson], which [highlightArgsJson] already made safe. */
    override fun highlightScript(argsJson: String): String = "($highlight)($argsJson)"

    private fun read(path: String): String =
        open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }.trimEnd().removeSuffix(";")

    companion object {
        private const val READABILITY = "js/readability/Readability.js"
        private const val READERABLE_LIBRARY = "js/readability/Readability-readerable.js"
        private const val EXTRACT = "js/skim-extract.js"
        private const val READERABLE = "js/skim-readerable.js"
        private const val HIGHLIGHT = "js/skim-highlight.js"

        /** The first Chromium that parses Readability: its source uses `??` and `?.`. */
        const val MIN_WEBVIEW_MAJOR_VERSION = 80

        /** Whether a WebView of [versionName] (`133.0.6943.137`) runs the page scripts; false when it names none. */
        fun runsOn(versionName: String?): Boolean {
            val major = versionName?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: return false
            return major >= MIN_WEBVIEW_MAJOR_VERSION
        }

        /** Whether the device's current WebView runs the page scripts. */
        fun runOnDevice(context: Context): Boolean = runsOn(WebViewCompat.getCurrentWebViewPackage(context)?.versionName)

        /**
         * [libraries] then the expression [entry], whose value the whole script evaluates to, in
         * one function scope. The entry goes inside parentheses that open on the `return` line, so a
         * comment or line break before it cannot end the statement early.
         */
        private fun scoped(libraries: List<String>, entry: String): String = buildString {
            append("(function () {\n")
            append("var module;\n")
            libraries.forEach { append(it).append(";\n") }
            append("return (\n").append(entry).append("\n);\n")
            append("})()")
        }
    }
}
