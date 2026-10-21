package com.dantech.skimbrowser.browser.page

/**
 * The scripts the tab manager runs in pages. The app reads them from its assets; tests give their
 * own. Each returns one JavaScript expression for `evaluateJavascript`.
 */
interface PageScriptSource {
    /**
     * False when this device's WebView cannot run the scripts at all (an old WebView cannot parse
     * Readability): no page is then reported as readable and none is read.
     */
    val isSupported: Boolean get() = true

    /** Evaluates to the page object that [parsePageContent] reads (Readability plus the block extractor). */
    fun extractScript(): String

    /** Evaluates to `true` when the page looks like an article, without running Readability itself. */
    fun readerableScript(): String

    /**
     * Highlights a block and evaluates to `true` when it found one. [argsJson] is the JSON object
     * built by [highlightArgsJson]; it is passed to the script as a value, never spliced into its
     * code.
     */
    fun highlightScript(argsJson: String): String
}
