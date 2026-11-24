package com.dantech.skimbrowser.ui.browsing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.AddressBar
import com.dantech.skimbrowser.ui.components.AddressLoadProgress
import com.dantech.skimbrowser.ui.components.CompactFieldChrome
import com.dantech.skimbrowser.ui.components.DisabledContentAlpha
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.SiteIndicator
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.TabsCountIcon
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.shell.AiFabMode
import com.dantech.skimbrowser.ui.shell.aiFabContentDescription
import com.dantech.skimbrowser.ui.theme.SkimChromeTheme
import com.dantech.skimbrowser.ui.theme.isDarkScheme
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

private val PillShadow = 3.dp

/** The pill's row padding before the address field and after Ask Skim. */
private val PillRowStart = 8.dp
private val PillRowEnd = 4.dp

/** The narrowest the host may get in the pill at the default text size: about five characters. */
private val MinHostWidth = 48.dp

/** Reader view, Save video, Tabs, More options and Ask Skim. */
private const val PILL_CONTROLS_WITH_VIDEO = 5

/** Ask Skim's slot in the collapsed chip, and the space after it. */
private val ChipAskSlot = 36.dp
private val ChipAskEnd = 2.dp
private const val FIND_FADE_MS = 160

/**
 * The floating pill of a page, on one row: the address (or Find in page), Reader view, Save video,
 * Tabs, More options and Ask Skim. There is no Back or Forward: Back is the system gesture.
 *
 * While [collapsed] the pill is a chip with the site mark, the host and Ask Skim, centered on the
 * pill's place and resting on its bottom edge. Tapping the chip calls [onExpand]. One container
 * morphs between the two sizes with the spatial spring while the content cross-fades; with Remove
 * animations on, both snap.
 *
 * The pill floats over web content, so it is drawn in the neutral chrome scheme. Insets are the
 * caller's.
 */
@Composable
fun BrowsingChrome(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onExpand: () -> Unit = {},
) {
    SkimChromeTheme { BrowsingPill(state, findQuery, actions, collapsed, onExpand, modifier) }
}

@Composable
private fun BrowsingPill(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
    collapsed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val motion = MaterialTheme.motionScheme
    val sizeSpec: FiniteAnimationSpec<IntSize> = if (reduced) snap() else motion.defaultSpatialSpec()
    val fadeSpec: FiniteAnimationSpec<Float> = if (reduced) snap() else motion.defaultEffectsSpec()
    // Wraps the container, so beside the chip the page is not covered by the bar's bounds.
    Box(
        modifier.semantics {
            isTraversalGroup = true
            traversalIndex = -1f
        },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDarkScheme()) colors.surfaceContainerHigh else colors.surfaceContainer,
            contentColor = colors.onSurfaceVariant,
            shadowElevation = PillShadow,
        ) {
            AnimatedContent(
                targetState = collapsed,
                transitionSpec = {
                    (fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)).using(SizeTransform { _, _ -> sizeSpec })
                },
                contentAlignment = Alignment.Center,
                label = "browsingPill",
            ) { chip ->
                if (chip) {
                    CollapsedChip(state, onAskPage = actions.onAskPage, onExpand = onExpand)
                } else {
                    FullPill(state, findQuery, actions)
                }
            }
        }
    }
}

@Composable
private fun FullPill(
    state: BrowsingUiState,
    findQuery: TextFieldState,
    actions: BrowsingActions,
) {
    val find = state.find
    val fade = if (rememberReducedMotion()) snap() else tween<Float>(FIND_FADE_MS)
    val videoInPill = state.videoAvailable && saveVideoFitsInPill()
    Box(Modifier.fillMaxWidth().height(ToolbarMetrics.AddressRowHeight)) {
        Row(
            Modifier.fillMaxSize().padding(start = PillRowStart, end = PillRowEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = find != null,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "findInPage",
            ) { open ->
                if (open && find != null) {
                    FindInPageBar(
                        query = findQuery,
                        state = find,
                        incognito = state.incognito,
                        onPrevious = actions.onFindPrevious,
                        onNext = actions.onFindNext,
                        onClose = actions.onCloseFind,
                        modifier = Modifier.fillMaxWidth(),
                        embedded = true,
                    )
                } else if (!open) {
                    AddressBar(
                        address = state.address,
                        indicator = state.indicator,
                        readerAvailable = state.readerAvailable,
                        loadProgress = null,
                        onSiteInfo = actions.onSiteInfo,
                        onEditAddress = actions.onEditAddress,
                        onReader = actions.onReader,
                        modifier = Modifier.fillMaxWidth(),
                        embedded = true,
                    )
                }
            }
            val tabsLabel = pluralStringResource(
                if (state.incognito) R.plurals.cd_incognito_tabs_open else R.plurals.cd_tabs_open,
                state.tabCount,
                state.tabCount,
            )
            AnimatedVisibility(visible = find == null, enter = fadeIn(fade), exit = fadeOut(fade)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // On a narrow window More options offers it instead (BrowsingRoute).
                    if (videoInPill) {
                        PillButton(onClick = actions.onDownloadVideo) {
                            Icon(
                                painterResource(R.drawable.ic_download),
                                stringResource(R.string.cd_download_video),
                                Modifier.size(20.dp),
                            )
                        }
                    }
                    PillButton(
                        onClick = actions.onTabs,
                        modifier = Modifier.semantics { contentDescription = tabsLabel },
                    ) {
                        TabsCountIcon(state.tabCount)
                    }
                    PillButton(onClick = actions.onMore) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert),
                            stringResource(R.string.cd_more_options),
                            Modifier.size(20.dp),
                        )
                    }
                    AskSkimButton(
                        mode = state.aiMode,
                        onClick = actions.onAskPage,
                        slot = ToolbarMetrics.BrowsingSlot,
                        mark = 32.dp,
                        icon = 18.dp,
                    )
                }
            }
        }
        if (find == null) {
            AddressLoadProgress(
                state.progress,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 36.dp).fillMaxWidth(),
                overflow = 0.dp,
            )
        }
    }
}

/**
 * The pill while the page scrolls down: the site mark, the host and, while Skim AI is on, the Sunny
 * mark. The chip is one button that brings the pill back; the mark opens Ask Skim.
 */
@Composable
private fun CollapsedChip(
    state: BrowsingUiState,
    onAskPage: () -> Unit,
    onExpand: () -> Unit,
) {
    val askSkim = state.aiMode == AiFabMode.Enabled
    val expandLabel = stringResource(R.string.cd_show_browser_controls)
    // Ask Skim is drawn over the end of the row as a sibling, and the row keeps its place free.
    // Inside the row, its 48 dp touch area would take the taps on the end of the host.
    Box(
        Modifier
            .height(ToolbarMetrics.BrowsingChipHeight)
            .widthIn(max = ToolbarMetrics.BrowsingChipMaxWidth),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(onClickLabel = expandLabel, role = Role.Button, onClick = onExpand)
                .padding(start = 14.dp, end = if (askSkim) ChipAskSlot + ChipAskEnd else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (state.indicator) {
                SiteIndicator.Secure -> R.drawable.ic_lock
                SiteIndicator.NotSecure -> R.drawable.ic_public
                SiteIndicator.Error -> R.drawable.ic_error
            }
            // The host is the chip's label; the mark beside it is decoration.
            Icon(painterResource(icon), null, Modifier.size(16.dp))
            Text(
                text = state.address,
                modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp, end = if (askSkim) 4.dp else 0.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (askSkim) {
            AskSkimButton(
                mode = AiFabMode.Enabled,
                onClick = onAskPage,
                slot = ChipAskSlot,
                mark = 28.dp,
                icon = 16.dp,
                modifier = Modifier.padding(end = ChipAskEnd),
            )
        }
    }
}

/**
 * Whether Save video has room in the pill of a window [windowWidth] wide, beside Reader view, Tabs,
 * More options and Ask Skim: the host keeps at least [MinHostWidth], more when [fontScale] makes the
 * text larger. Without room the pill leaves Save video out and More options offers Download video.
 */
internal fun saveVideoFitsInPill(windowWidth: Dp, fontScale: Float): Boolean {
    val controls = ToolbarMetrics.BrowsingSlot * PILL_CONTROLS_WITH_VIDEO
    val pill = windowWidth - ToolbarMetrics.BrowsingSideInset * 2
    val host = pill - PillRowStart - PillRowEnd - controls - CompactFieldChrome
    return host >= MinHostWidth * fontScale.coerceAtLeast(1f)
}

/** [saveVideoFitsInPill] for the window and the text size this composition draws in. */
@Composable
internal fun saveVideoFitsInPill(): Boolean {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    return saveVideoFitsInPill(width, density.fontScale)
}

/** A button of the pill outside the address field: a 40 dp slot, touching its neighbours. */
@Composable
private fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(ToolbarMetrics.BrowsingSlot)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Ask Skim on a page: the Sunny mark in tertiaryContainer, as every AI surface. While [mode] is not
 * [AiFabMode.Enabled] (incognito, a failed or warned page) the shape is drawn disabled, without a
 * click action, and its label says why.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AskSkimButton(
    mode: AiFabMode,
    onClick: () -> Unit,
    slot: Dp,
    mark: Dp,
    icon: Dp,
    modifier: Modifier = Modifier,
) {
    val label = aiFabContentDescription(mode, isNewTabPage = false)
    if (mode == AiFabMode.Enabled) {
        Box(
            modifier
                .size(slot)
                .clip(CircleShape)
                .clickable(onClick = onClick, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            SunnyMark(size = mark, iconSize = icon, contentDescription = label)
        }
    } else {
        val colors = MaterialTheme.colorScheme
        Box(
            modifier
                .size(slot)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            ShapeArt(
                shape = MaterialShapes.Sunny,
                size = mark,
                icon = R.drawable.ic_auto_awesome,
                iconSize = icon,
                container = colors.onSurface.copy(alpha = if (isDarkScheme()) 0.12f else 0.10f),
                content = colors.onSurface.copy(alpha = DisabledContentAlpha),
                contentDescription = label,
            )
        }
    }
}
