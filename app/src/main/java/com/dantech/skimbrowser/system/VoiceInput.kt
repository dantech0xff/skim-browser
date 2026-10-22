package com.dantech.skimbrowser.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import com.dantech.skimbrowser.browser.cutTo
import com.dantech.skimbrowser.browser.toVisibleText

/**
 * Voice search (04, 09c): Android's own speech input, asked for a web search. The mic shows only
 * when [isAvailable]; what was said goes into the search field (04b).
 */
object VoiceInput {

    /** The longest text taken from speech input; the recognizer is another app. */
    internal const val MAX_TEXT_LENGTH = 1_000

    /**
     * True when an app takes speech input. The manifest declares the speech intent in its queries,
     * so from Android 11 on the answer is not limited by package visibility.
     */
    fun isAvailable(context: Context): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val packages = context.packageManager
        // Only activities that take implicit intents can be started with this one.
        val recognizers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packages.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return recognizers.isNotEmpty()
    }

    /**
     * Starts speech input for a web search and returns its best result as plain visible text (no
     * control, bidi or zero-width characters), at most [MAX_TEXT_LENGTH] characters; null when the
     * user cancels or nothing was recognized.
     */
    class RecognizeSpeech : ActivityResultContract<Unit, String?>() {

        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)

        override fun parseResult(resultCode: Int, intent: Intent?): String? {
            if (resultCode != Activity.RESULT_OK) return null
            val best = intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return null
            return best.toVisibleText().cutTo(MAX_TEXT_LENGTH).trimEnd().ifEmpty { null }
        }
    }
}
