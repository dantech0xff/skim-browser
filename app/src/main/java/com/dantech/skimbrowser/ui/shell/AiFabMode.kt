package com.dantech.skimbrowser.ui.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabRuntime
import com.dantech.skimbrowser.ui.components.AiFab

/** What the AI FAB of a tab's toolbar can do. */
enum class AiFabMode {
    Enabled,

    /** Skim AI is off in incognito (09b, 09c). */
    OffInIncognito,

    /** The page failed to load (11): there is nothing to read until it loads. */
    UnavailableUntilLoaded,
}

/** The AI FAB of [tab] with its page's [runtime]: off in incognito and while the page failed to load. */
fun aiFabMode(tab: Tab, runtime: TabRuntime): AiFabMode = when {
    tab.incognito -> AiFabMode.OffInIncognito
    runtime.loadError != null -> AiFabMode.UnavailableUntilLoaded
    else -> AiFabMode.Enabled
}

/** The label of the AI FAB: Ask Skim on the New tab page (04), about this page elsewhere. */
@StringRes
internal fun aiFabLabel(mode: AiFabMode, isNewTabPage: Boolean): Int = when (mode) {
    AiFabMode.Enabled -> if (isNewTabPage) R.string.ask_skim else R.string.cd_ask_skim_about_page
    AiFabMode.OffInIncognito -> R.string.cd_ask_skim_off_incognito
    AiFabMode.UnavailableUntilLoaded -> R.string.cd_ask_skim_unavailable
}

@Composable
fun aiFabContentDescription(mode: AiFabMode, isNewTabPage: Boolean): String = stringResource(aiFabLabel(mode, isNewTabPage))

/**
 * The AI FAB of every toolbar that shows a tab (04, 05, 05c, 08, 09c, 11): enabled or drawn
 * disabled by [aiFabMode], labelled for what it opens.
 */
@Composable
fun TabAiFab(tab: Tab, runtime: TabRuntime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mode = aiFabMode(tab, runtime)
    AiFab(
        enabled = mode == AiFabMode.Enabled,
        contentDescription = aiFabContentDescription(mode, isNewTabPage = tab.url == null),
        onClick = onClick,
        modifier = modifier,
    )
}
