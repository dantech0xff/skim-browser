package com.dantech.skimbrowser.browser

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What an incognito tab keeps away from the rest of the device, on the device's WebView: the
 * keyboard is asked not to learn from what is typed, the autofill service is not offered the page's
 * fields, and the page's console never reaches the system log. A normal tab keeps the defaults.
 */
@RunWith(AndroidJUnit4::class)
class IncognitoPrivacyDeviceTest {

    @get:Rule
    val rig = BrowserRig()

    @Before
    fun profilesAreSupported() {
        WebViewSupport.assumeProfiles()
    }

    @Test
    fun anIncognitoPageAsksTheKeyboardNotToLearnWhatIsTypedAndANormalPageDoesNot() {
        rig.showCurrentTab()

        val normal = editorOfTheField(incognito = false)
        val incognito = editorOfTheField(incognito = true)

        assertEquals("a normal page leaves the keyboard its learning", 0, normal.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertNotEquals("an incognito page asks it not to learn", 0, incognito.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    }

    @Test
    fun anIncognitoTabsWebViewIsLeftOutOfAutofillAndANormalTabsIsNot() {
        val normal = rig.newTab(rig.pages.url("/form.html"))
        val incognito = rig.newTab(rig.pages.url("/form.html"), incognito = true)

        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, onMain { rig.webViewOf(incognito).importantForAutofill })
        assertNotEquals("a normal page's fields still reach autofill", View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, onMain { rig.webViewOf(normal).importantForAutofill })
    }

    @Test
    fun anIncognitoPagesConsoleStaysOutOfTheSystemLogAndANormalPagesDoesNot() {
        val incognito = rig.newTab(rig.pages.url("/plain.html"), incognito = true)
        val normal = rig.newTab(rig.pages.url("/plain.html"))
        listOf(incognito, normal).forEach { assertTrue(runBlocking { rig.tabs.awaitPageReady(it) }) }
        val hidden = "skim-console-${UUID.randomUUID()}"
        val shown = "skim-console-${UUID.randomUUID()}"

        // In this order, so that once the normal page's line is in the log the incognito one would be too.
        rig.evaluate(incognito, "console.log('$hidden')")
        rig.evaluate(normal, "console.log('$shown')")

        waitUntil("the normal page's console line in the system log") { shown in consoleLog() }
        assertFalse("the incognito page's line never is", hidden in consoleLog())
    }

    /** Opens the form page in a new current tab, taps its field and returns what the WebView tells the keyboard about it. */
    private fun editorOfTheField(incognito: Boolean): EditorInfo {
        val url = rig.pages.url("/form.html")
        val before = rig.webViewsInWindow().singleOrNull()
        val id = rig.newTab(url, incognito = incognito)
        rig.awaitLoaded(id, url)
        val view = rig.shownWebView(other = before)
        waitUntil("the field to take the keyboard") {
            Finger.tap(view, fractionY = FIELD_Y)
            holdsFor(FOCUS_WAIT_MS) { !onMain { view.onCheckIsTextEditor() } }.not()
        }
        val editor = EditorInfo()
        assertNotNull("the field has an input connection", onMain { view.onCreateInputConnection(editor) })
        return editor
    }

    /** The WebView's console lines in the system log (its tag is `chromium`). */
    private fun consoleLog(): String = shell("logcat -d -s chromium:I")

    private companion object {
        /** Inside the page's text field, over its top 40%. */
        const val FIELD_Y = 0.2f
        const val FOCUS_WAIT_MS = 1_000L
    }
}
