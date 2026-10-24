package com.dantech.skimbrowser.browser

import com.dantech.skimbrowser.browser.page.PageScriptSource

/** Page scripts as recognizable markers, so a test can tell which one ran and with what. */
class FakePageScripts : PageScriptSource {
    /** What [isSupported] answers: false makes the tab manager read no page. */
    var supported = true

    override val isSupported: Boolean get() = supported

    override fun extractScript() = EXTRACT

    override fun readerableScript() = READERABLE

    override fun highlightScript(argsJson: String) = "$HIGHLIGHT($argsJson)"

    companion object {
        const val EXTRACT = "skimExtract()"
        const val READERABLE = "skimReaderable()"
        const val HIGHLIGHT = "skimHighlight"
    }
}
