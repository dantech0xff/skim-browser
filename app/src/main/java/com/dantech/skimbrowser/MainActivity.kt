package com.dantech.skimbrowser

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * The only activity. Rotation, dark mode and font changes are declared in the manifest's
 * configChanges, so they reach the composition without recreating the activity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContent {
            SkimTheme(ThemeSetting.System) {
                Surface(Modifier.fillMaxSize()) {}
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
