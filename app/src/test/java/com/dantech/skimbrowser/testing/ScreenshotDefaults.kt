package com.dantech.skimbrowser.testing

import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * Robolectric qualifiers of a phone board: 412 × 915 dp at xhdpi, so captures are 824 × 1830 px,
 * the size of the PNGs in design/screens. Use with `@Config(qualifiers = PHONE_QUALIFIERS)`.
 */
const val PHONE_QUALIFIERS = "w412dp-h915dp-xhdpi"

/** The top 24 dp of every phone board stands for the status bar. */
val BOARD_STATUS_BAR_HEIGHT: Dp = 24.dp

/**
 * Sets [content] the way the boards are drawn: SkimTheme with the brand scheme (dynamic color off,
 * the boards use seed #1A56DB) and a status bar of [statusBarInset] at the top.
 *
 * Robolectric draws no system bars and reports zero insets, so the status bar inset is dispatched
 * to the composition once it is attached. `WindowInsets.statusBars` then reports it, and
 * edge-to-edge layouts (statusBarsPadding, top app bars) land where the boards draw them.
 */
fun ComposeContentTestRule.setSkimContent(
    dark: Boolean = false,
    incognito: Boolean = false,
    statusBarInset: Dp = BOARD_STATUS_BAR_HEIGHT,
    content: @Composable () -> Unit,
) {
    lateinit var composeView: View
    var statusBarPx = 0
    setContent {
        composeView = LocalView.current
        statusBarPx = with(LocalDensity.current) { statusBarInset.roundToPx() }
        // Reading the insets keeps Compose's inset listener installed on the view for the whole
        // test, so the dispatch below is received even before the content reads insets itself.
        WindowInsets.statusBars
        SkimTheme(
            theme = if (dark) ThemeSetting.Dark else ThemeSetting.Light,
            incognito = incognito,
            dynamicColor = false,
            content = content,
        )
    }
    runOnIdle {
        val statusBar = Insets.of(0, statusBarPx, 0, 0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), statusBar)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), statusBar)
            .setVisible(WindowInsetsCompat.Type.statusBars(), true)
            .build()
        ViewCompat.dispatchApplyWindowInsets(composeView, insets)
    }
}
