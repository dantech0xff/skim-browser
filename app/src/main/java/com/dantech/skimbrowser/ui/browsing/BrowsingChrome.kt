package com.dantech.skimbrowser.ui.browsing

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.AddressBar
import com.dantech.skimbrowser.ui.components.AddressLoadProgress
import com.dantech.skimbrowser.ui.components.TabsCountIcon
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.components.isDarkScheme

private val Slot = 32.dp
private val NavSlot = 22.dp
private val PillShadow = 6.dp

/**
 * One floating pill for a page: small Back and Forward, the address (or Find in page), Reader view
 * and Tabs, on a single row. Ask Skim is hidden until it comes back. Insets are the caller's.
 */
@Composable
fun BrowsingChrome(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = colors.onSurface
    val dark = isDarkScheme()
    val solid = rememberSolidChrome()
    val fill = (if (dark) colors.surfaceContainerHigh else colors.surfaceContainer)
        .copy(alpha = glassChromeAlpha(dark, solid))
    val highlight = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.72f)
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ToolbarMetrics.AddressRowHeight)
                .shadow(PillShadow, CircleShape)
                .background(fill, CircleShape)
                .clip(CircleShape),
        ) {
            Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(highlight, Color.Transparent))),
            )
            CompositionLocalProvider(LocalContentColor provides content) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavIcon(R.drawable.ic_chevron_left, stringResource(R.string.cd_back), actions.onBack, state.backEnabled)
                    NavIcon(R.drawable.ic_chevron_right, stringResource(R.string.cd_forward), actions.onForward, state.forwardEnabled)
                    val find = state.find
                    if (find != null) {
                        FindInPageBar(
                            query = findQuery,
                            state = find,
                            incognito = state.incognito,
                            onPrevious = actions.onFindPrevious,
                            onNext = actions.onFindNext,
                            onClose = actions.onCloseFind,
                            modifier = Modifier.weight(1f),
                            embedded = true,
                        )
                    } else {
                        AddressBar(
                            address = state.address,
                            indicator = state.indicator,
                            readerAvailable = state.readerAvailable,
                            loadProgress = null,
                            onSiteInfo = actions.onSiteInfo,
                            onEditAddress = actions.onEditAddress,
                            onReader = actions.onReader,
                            modifier = Modifier.weight(1f),
                            embedded = true,
                        )
                        val tabsLabel = pluralStringResource(
                            if (state.incognito) R.plurals.cd_incognito_tabs_open else R.plurals.cd_tabs_open,
                            state.tabCount,
                            state.tabCount,
                        )
                        Box(
                            Modifier
                                .size(Slot)
                                .clip(CircleShape)
                                .clickable(onClick = actions.onTabs, role = Role.Button)
                                .semantics { contentDescription = tabsLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            TabsCountIcon(state.tabCount)
                        }
                    }
                }
            }
                if (state.find == null) {
                    AddressLoadProgress(
                        state.progress,
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 36.dp).fillMaxWidth(),
                        overflow = 0.dp,
                    )
                }
            }
        }
    }
}

/**
 * How opaque the browsing pill is. High contrast text asks for a nearly solid bar so the address
 * and icons stay readable. Otherwise the bar is a frosted tint: the page shows through, and the
 * tint is still strong enough on a white page, a dark page, or a photograph.
 *
 * A live blur of the WebView is not used. The page is a hardware surface, so the public blur
 * (`Modifier.blur`, `RenderEffect`) cannot see it, and copying that surface every frame would
 * stall scrolling. A second window with blur-behind would blur a rectangle behind this pill and
 * would split Find, the keyboard, and scroll-to-hide out of this composition.
 */
internal fun glassChromeAlpha(dark: Boolean, highContrast: Boolean): Float = when {
    highContrast && dark -> 0.94f
    highContrast -> 0.96f
    dark -> 0.80f
    else -> 0.84f
}

@Composable
private fun rememberSolidChrome(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var solid by remember { mutableStateOf(highContrastText(manager)) }
    if (Build.VERSION.SDK_INT >= 36 && manager != null) {
        DisposableEffect(manager) {
            val listener = AccessibilityManager.HighContrastTextStateChangeListener { enabled -> solid = enabled }
            manager.addHighContrastTextStateChangeListener(context.mainExecutor, listener)
            onDispose { manager.removeHighContrastTextStateChangeListener(listener) }
        }
    }
    return solid
}

private fun highContrastText(manager: AccessibilityManager?): Boolean =
    manager != null && Build.VERSION.SDK_INT >= 36 && manager.isHighContrastTextEnabled

/** A chevron for history, narrower than an arrow so the address keeps the width. */
@Composable
private fun NavIcon(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val tint = LocalContentColor.current.let { color ->
        if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        Modifier
            .size(NavSlot)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, Modifier.size(20.dp), tint = tint)
    }
}
