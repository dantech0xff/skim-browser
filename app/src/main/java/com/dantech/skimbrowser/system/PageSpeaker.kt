package com.dantech.skimbrowser.system

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads an answer with the platform [TextToSpeech]. Nothing is stored. [shutdown] releases the
 * engine when the sheet leaves the screen.
 */
class PageSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String?>? = null

    /** Speaks [text]. [languageTag] selects a voice when the engine has one; otherwise the default. */
    fun speak(text: String, languageTag: String? = null) {
        val words = text.trim()
        if (words.isEmpty()) return
        val current = engine
        if (current == null) {
            pending = words to languageTag
            engine = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                val queued = pending
                pending = null
                if (ready && queued != null) say(queued.first, queued.second)
            }
            return
        }
        if (ready) say(words, languageTag)
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    fun shutdown() {
        pending = null
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun say(text: String, languageTag: String?) {
        val speaker = engine ?: return
        val requested = if (languageTag != null) Locale.forLanguageTag(languageTag) else Locale.getDefault()
        val locale = if (speaker.isLanguageAvailable(requested) >= TextToSpeech.LANG_AVAILABLE) {
            requested
        } else {
            Locale.getDefault()
        }
        speaker.language = locale
        speaker.speak(text, TextToSpeech.QUEUE_FLUSH, null, "skim-read-aloud")
    }
}
