package com.dantech.skimbrowser.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/** Sizes of the floating toolbars (04, 05, 05c, 06d, 08, 09, 09b, 09c, 11). */
object ToolbarMetrics {
    /** Height of the toolbar; its bounds are exactly the drawn bar. */
    val Height = 64.dp

    /** Gap between the bottom of the toolbar and the navigation bar inset. */
    val BottomOffset = 24.dp

    /** Gap between the toolbar and its FAB. */
    val FabGap = 8.dp
}

/** Content at 38% for disabled controls, as drawn on every board. */
internal const val DisabledContentAlpha = 0.38f

/**
 * Whether the colors in use are a dark scheme: incognito (09b, 09c), or Theme Dark (12). Some roles
 * differ between the two drawings, such as the toolbar container and the disabled container.
 */
@Composable
@ReadOnlyComposable
internal fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * The browser's floating toolbar: Back, Forward, Tabs with the tab count and More options, with
 * [aiFab] beside it (usually an [AiFab]).
 *
 * The component does not handle insets. Place it at the bottom center of the screen with
 * `Modifier.navigationBarsPadding().padding(bottom = ToolbarMetrics.BottomOffset)`: its bounds are
 * the 64 dp bar, so that padding is exactly the drawn gap.
 *
 * @param incognito reads the Tabs button as "Incognito tabs, N open" (09c); the dark colors come
 *   from the theme.
 */
@Composable
fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    incognito: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit,
    aiFab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkimFloatingToolbar(
        label = stringResource(R.string.cd_browser_controls),
        fab = aiFab,
        modifier = modifier,
    ) {
        ToolbarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.cd_back), onBack, enabled = canGoBack)
        ToolbarIconButton(
            R.drawable.ic_arrow_forward,
            stringResource(R.string.cd_forward),
            onForward,
            enabled = canGoForward,
        )
        val tabsLabel = pluralStringResource(
            if (incognito) R.plurals.cd_incognito_tabs_open else R.plurals.cd_tabs_open,
            tabCount,
            tabCount,
        )
        IconButton(
            onClick = onTabs,
            modifier = Modifier.semantics { contentDescription = tabsLabel },
            colors = toolbarIconButtonColors(LocalContentColor.current),
        ) {
            TabsCountIcon(tabCount)
        }
        ToolbarIconButton(R.drawable.ic_more_vert, stringResource(R.string.cd_more_options), onMore)
    }
}

/**
 * The toolbar of Tabs (09) and Incognito tabs (09b): Ask Skim about these tabs, New incognito tab
 * and Close all, beside a New tab (or New incognito tab) FAB in primaryContainer.
 *
 * Ask Skim is drawn disabled when [aiEnabled] is false and always in incognito, where Skim AI is
 * off. Placement is the same as [BrowserToolbar].
 *
 * @param onNewIncognitoTab null hides the New incognito tab button (09b, where the FAB does it).
 */
@Composable
fun TabSwitcherToolbar(
    incognito: Boolean,
    aiEnabled: Boolean,
    onAskSkim: () -> Unit,
    onNewIncognitoTab: (() -> Unit)?,
    onCloseAll: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SkimFloatingToolbar(
        label = stringResource(R.string.cd_tab_actions),
        fab = {
            FloatingToolbarDefaults.StandardFloatingActionButton(
                onClick = onNewTab,
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    stringResource(if (incognito) R.string.new_incognito_tab else R.string.new_tab),
                )
            }
        },
        modifier = modifier,
    ) {
        val askEnabled = aiEnabled && !incognito
        ToolbarIconButton(
            icon = if (askEnabled) R.drawable.ic_auto_awesome_filled else R.drawable.ic_auto_awesome,
            contentDescription = stringResource(
                if (incognito) R.string.cd_ask_skim_off_incognito else R.string.cd_ask_skim_about_tabs,
            ),
            onClick = onAskSkim,
            enabled = askEnabled,
            contentColor = colors.tertiary,
        )
        if (onNewIncognitoTab != null) {
            ToolbarIconButton(
                R.drawable.ic_visibility_off,
                stringResource(R.string.new_incognito_tab),
                onNewIncognitoTab,
            )
        }
        ToolbarIconButton(
            R.drawable.ic_delete,
            stringResource(if (incognito) R.string.cd_close_all_incognito_tabs else R.string.cd_close_all_tabs),
            onCloseAll,
        )
    }
}

/**
 * The AI FAB of the browser toolbar: Sunny's auto_awesome on tertiaryContainer. On a page it opens
 * 06, on Home 06b.
 *
 * Disabled in incognito (09c) and while a page failed to load (11).
 * StandardFloatingActionButton has no enabled state, so the disabled AI FAB is drawn by hand:
 * onSurface at 10% (12% in dark) behind the outlined icon at 38%, no shadow, and no click action.
 *
 * Like every toolbar FAB it fills the slot the toolbar gives it (56 dp).
 */
@Composable
fun AiFab(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (enabled) {
        FloatingToolbarDefaults.StandardFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        ) {
            Icon(painterResource(R.drawable.ic_auto_awesome_filled), contentDescription)
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(FloatingActionButtonDefaults.shape)
                .background(colors.onSurface.copy(alpha = if (isDarkScheme()) 0.12f else 0.10f))
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_auto_awesome),
                contentDescription,
                tint = colors.onSurface.copy(alpha = DisabledContentAlpha),
            )
        }
    }
}

/**
 * The tab count inside the Tabs button: a 22 dp square with a 2 dp border and radius 7 in the
 * content color. 100 tabs or more read "99+", which widens the square. The number is part of the
 * icon, so it is hidden from accessibility; the button's label says it ("Tabs, 7 open").
 */
@Composable
fun TabsCountIcon(count: Int, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val text = if (count > MaxShownTabCount) {
        stringResource(R.string.tabs_count_overflow)
    } else {
        count.coerceAtLeast(0).toString()
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = TabsCountSize)
            .height(TabsCountSize)
            .border(2.dp, color, RoundedCornerShape(7.dp))
            .padding(horizontal = 3.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMediumEmphasized, maxLines = 1)
    }
}

private const val MaxShownTabCount = 99
private val TabsCountSize = 22.dp

/**
 * A HorizontalFloatingToolbar drawn as on the boards: 64 high, surfaceContainer (surfaceContainerHigh
 * in dark), icons in onSurfaceVariant, padding 0 by 8 and 4 between buttons, the FAB 8 to its right.
 * Elevation keeps the Compose defaults.
 */
@Composable
private fun SkimFloatingToolbar(
    label: String,
    fab: @Composable () -> Unit,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = fab,
        // The toolbar layout is otherwise as tall as an expanded FAB (80 dp) and would draw the bar
        // 8 dp above its bottom edge; fixing the height makes the bounds the drawn bar.
        modifier = modifier.height(ToolbarMetrics.Height),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = if (isDarkScheme()) colors.surfaceContainerHigh else colors.surfaceContainer,
            toolbarContentColor = colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.semantics {
                isTraversalGroup = true
                contentDescription = label
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
) {
    IconButton(onClick = onClick, enabled = enabled, colors = toolbarIconButtonColors(contentColor)) {
        Icon(painterResource(icon), contentDescription)
    }
}

/** Disabled toolbar icons are onSurface at 38%, whatever their enabled color. */
@Composable
private fun toolbarIconButtonColors(contentColor: Color): IconButtonColors =
    IconButtonDefaults.iconButtonColors(
        contentColor = contentColor,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha),
    )

@PreviewLightDark
@Composable
private fun BrowserToolbarPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            BrowserToolbar(
                canGoBack = true,
                canGoForward = false,
                tabCount = 7,
                incognito = false,
                onBack = {},
                onForward = {},
                onTabs = {},
                onMore = {},
                aiFab = {
                    AiFab(enabled = true, stringResource(R.string.cd_ask_skim_about_page), onClick = {})
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BrowserToolbarIncognitoPreview() {
    SkimTheme(ThemeSetting.System, incognito = true, dynamicColor = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            BrowserToolbar(
                canGoBack = false,
                canGoForward = false,
                tabCount = 2,
                incognito = true,
                onBack = {},
                onForward = {},
                onTabs = {},
                onMore = {},
                aiFab = {
                    AiFab(enabled = false, stringResource(R.string.cd_ask_skim_off_incognito), onClick = {})
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TabSwitcherToolbarPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            TabSwitcherToolbar(
                incognito = false,
                aiEnabled = true,
                onAskSkim = {},
                onNewIncognitoTab = {},
                onCloseAll = {},
                onNewTab = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AiFabPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(56.dp)) {
                AiFab(enabled = true, stringResource(R.string.cd_ask_skim_about_page), onClick = {})
            }
            Box(Modifier.size(56.dp)) {
                AiFab(enabled = false, stringResource(R.string.cd_ask_skim_unavailable), onClick = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TabsCountIconPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(7, 42, 120).forEach { TabsCountIcon(it) }
        }
    }
}
