package com.dantech.skimbrowser.ui.incognito

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.SearchBarField
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.StaticListItem
import com.dantech.skimbrowser.ui.components.ToolbarMetrics

/**
 * The New tab page of an incognito tab (09c), in the dark scheme the shell gives incognito content:
 * the incognito search bar fixed at the top (visibility_off, "Search or type URL", the mic while an
 * app takes speech input), then "You're incognito" and what Skim does and does not keep, which
 * scrolls above the floating [toolbar].
 *
 * The page only states things: nothing on it can be tapped but the search bar, its mic and the
 * toolbar.
 *
 * @param onSearch opens the dark Search & suggestions (04b) with the keyboard up.
 * @param onVoice starts speech input; shown only while [voiceAvailable].
 */
@Composable
fun IncognitoNewTabScreen(
    voiceAvailable: Boolean,
    onSearch: () -> Unit,
    onVoice: () -> Unit,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            SearchBarField(
                incognito = true,
                onClick = onSearch,
                onVoice = if (voiceAvailable) onVoice else null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 32.dp, bottom = ToolbarMetrics.Height + ToolbarMetrics.BottomOffset + navigationBar),
            ) {
                IncognitoHero()
                // 32 dp between the hero and the first label, as drawn; the label keeps 12 of them.
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.incognito_wont_save), first = true)
                SegmentedColumn {
                    StaticListItem(R.drawable.ic_history, stringResource(R.string.incognito_pages_you_visit), 0, 3)
                    StaticListItem(R.drawable.ic_cookie, stringResource(R.string.cookies_and_site_data), 1, 3)
                    StaticListItem(R.drawable.ic_keyboard, stringResource(R.string.incognito_form_input), 2, 3)
                }
                SectionLabel(stringResource(R.string.incognito_still_visible_to), first = false)
                SegmentedColumn {
                    StaticListItem(R.drawable.ic_public, stringResource(R.string.incognito_websites_you_visit), 0, 3)
                    StaticListItem(R.drawable.ic_work, stringResource(R.string.incognito_employer_or_school), 1, 3)
                    StaticListItem(R.drawable.ic_router, stringResource(R.string.incognito_internet_provider), 2, 3)
                }
                Text(
                    text = stringResource(R.string.incognito_footer),
                    modifier = Modifier.padding(start = 32.dp, top = 16.dp, end = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ToolbarMetrics.BottomOffset),
        ) {
            toolbar()
        }
    }
}

/**
 * The hero of 09c, 24 dp from the edges: visibility_off (36) on a 72 dp Cookie12Sided in
 * secondaryContainer, the title in headlineMediumEmphasized 24 dp under it and the body 8 dp under
 * that.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IncognitoHero() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 24.dp)) {
        ShapeArt(
            shape = MaterialShapes.Cookie12Sided,
            size = 72.dp,
            icon = R.drawable.ic_visibility_off,
            iconSize = 36.dp,
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.incognito_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.incognito_body),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
