package com.dantech.skimbrowser.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Open-source licenses (12). The list fills the space under the large title and scrolls on its
 * own. [produceLibraries] reads the license set the build generates, including the bundled font,
 * icons and Readability.js.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    LargeTitleScaffold(
        title = stringResource(R.string.settings_open_source_licenses),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier.fillParentMaxSize(),
            )
        }
    }
}

/** Open-source licenses, reached from Settings. */
@Composable
fun LicensesRoute(navigator: SkimNavigator) {
    LicensesScreen(onBack = navigator::back)
}
