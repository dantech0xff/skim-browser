package com.dantech.skimbrowser.ui.browsing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val PillShadow = 3.dp

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
    val content = colors.onSurfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDarkScheme()) colors.surfaceContainerHigh else colors.surfaceContainer,
            contentColor = content,
            shadowElevation = PillShadow,
            modifier = Modifier.fillMaxWidth().height(ToolbarMetrics.AddressRowHeight),
        ) {
            Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentColor provides content) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavIcon(R.drawable.ic_arrow_back, stringResource(R.string.cd_back), actions.onBack, state.backEnabled)
                    NavIcon(R.drawable.ic_arrow_forward, stringResource(R.string.cd_forward), actions.onForward, state.forwardEnabled)
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

/** A glyph for history, smaller than the other slots so the address keeps the width. */
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
        Icon(painterResource(icon), description, Modifier.size(18.dp), tint = tint)
    }
}
