package com.dantech.skimbrowser.system

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Whether the mic shows, and what Android's speech input is asked for and hands back. */
@RunWith(AndroidJUnit4::class)
class VoiceInputTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recognizer = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.voicesearch.Recognizer")
    private val contract = VoiceInput.RecognizeSpeech()

    // Whether the mic shows

    @Test
    fun withoutASpeechAppTheMicIsHidden() {
        assertFalse(VoiceInput.isAvailable(context))
    }

    @Test
    fun aSpeechAppShowsTheMic() {
        install(speechInput(withDefaultCategory = true))

        assertTrue(VoiceInput.isAvailable(context))
    }

    @Config(sdk = [26])
    @Test
    fun beforeAndroid13ASpeechAppShowsTheMicToo() {
        assertFalse(VoiceInput.isAvailable(context))

        install(speechInput(withDefaultCategory = true))

        assertTrue(VoiceInput.isAvailable(context))
    }

    @Test
    fun aSpeechAppThatCannotBeStartedThisWayDoesNotShowTheMic() {
        // Without the default category an implicit intent never starts it.
        install(speechInput(withDefaultCategory = false))

        assertFalse(VoiceInput.isAvailable(context))
    }

    // What speech input is asked for

    @Test
    fun speechInputIsAskedForAWebSearch() {
        val intent = contract.createIntent(context, Unit)

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
    }

    // What comes back

    @Test
    fun theBestResultIsTheText() {
        assertEquals("heat pump", contract.parseResult(Activity.RESULT_OK, results("heat pump", "heat bump", "eat pump")))
    }

    @Test
    fun cancellingGivesNoText() {
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, results("heat pump")))
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, null))
    }

    @Test
    fun nothingRecognizedGivesNoText() {
        assertNull(contract.parseResult(Activity.RESULT_OK, null))
        assertNull(contract.parseResult(Activity.RESULT_OK, Intent()))
        assertNull(contract.parseResult(Activity.RESULT_OK, results()))
        assertNull(contract.parseResult(Activity.RESULT_OK, results("   ")))
        assertNull(contract.parseResult(Activity.RESULT_OK, results("\u200B\u202E")))
    }

    @Test
    fun theTextIsPlainVisibleText() {
        assertEquals("heat pump", contract.parseResult(Activity.RESULT_OK, results("\u202Eheat\u0000 pump\u200B\n")))
    }

    @Test
    fun aVeryLongResultIsCut() {
        val text = contract.parseResult(Activity.RESULT_OK, results("a".repeat(5_000)))

        assertEquals(VoiceInput.MAX_TEXT_LENGTH, text?.length)
    }

    private fun results(vararg texts: String) =
        Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(*texts))

    private fun speechInput(withDefaultCategory: Boolean) = IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        if (withDefaultCategory) addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun install(filter: IntentFilter) {
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(recognizer)
        packages.addIntentFilterForActivity(recognizer, filter)
    }
}
