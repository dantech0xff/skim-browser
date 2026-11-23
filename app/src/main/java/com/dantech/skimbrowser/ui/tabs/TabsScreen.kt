package com.dantech.skimbrowser.ui.tabs

import androidx.compose.ui.platform.LocalContext
import com.dantech.skimbrowser.system.confirmIncognito
import com.dantech.skimbrowser.ui.navigation.rememberShell
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.dantech.skimbrowser.ui.components.TabCardShape
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion
import kotlin.math.abs
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.tab.GroupingBannerState
import com.dantech.skimbrowser.browser.ThumbnailStore
import com.dantech.skimbrowser.ui.components.CompactIconButton
import com.dantech.skimbrowser.ui.components.ConnectedToggleGroup
import com.dantech.skimbrowser.ui.components.CountBadge
import com.dantech.skimbrowser.ui.components.TabCard
import com.dantech.skimbrowser.ui.components.TabSwitcherToolbar
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.navigation.RenameGroupKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.TabsKey
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the grid asks the screen's owner to do. Navigation stays with the route. */
class TabsActions(
    val onSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onSelectMode: (incognito: Boolean) -> Unit,
    val onOpen: (String) -> Unit,
    val onClose: (String) -> Unit,
    val onMove: (String, Int) -> Unit,
    val onRename: (Long) -> Unit,
    val onUngroup: (Long) -> Unit,
    val onCloseGroup: (Long) -> Unit,
    val onUndoGrouping: () -> Unit,
    val onAskSkim: () -> Unit,
    val onNewIncognitoTab: (() -> Unit)?,
    val onCloseAll: () -> Unit,
    val onNewTab: () -> Unit,
)

/** Tabs (09) or Incognito tabs (09b), from [key]. */
@Composable
fun TabsRoute(key: TabsKey, navigator: SkimNavigator) {
    val context = LocalContext.current
    val shell = rememberShell()
    val model = hiltViewModel<TabsViewModel, TabsViewModel.Factory>(key = "tabs-${key.incognito}") { factory ->
        factory.create(
            incognito = key.incognito,
            grouping = shell.grouping().banner,
            grouper = if (key.incognito) null else shell.grouping(),
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(key.incognito) {
        if (key.incognito) return@LaunchedEffect
        model.attachGrouping()
        try {
            awaitCancellation()
        } finally {
            model.detachGrouping()
        }
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    val query = rememberTextFieldState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(searching, query) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { query.text.toString() }
            .map { it.trim() }
            .distinctUntilChanged()
            .collect { model.setQuery(it) }
    }
    var focused by rememberSaveable(key.focusTabId) { mutableStateOf(false) }
    LaunchedEffect(state.sections, searching) {
        if (focused || searching) return@LaunchedEffect
        val tabId = key.focusTabId ?: return@LaunchedEffect
        val index = focusHeaderIndex(state, tabId, showTopBanner = topBanner(state, searching = false)) ?: return@LaunchedEffect
        gridState.scrollToItem(index)
        focused = true
    }
    TabsScreen(
        state = state,
        searchState = if (searching) query else null,
        gridState = gridState,
        actions = TabsActions(
            onSearch = { searching = true },
            onCloseSearch = {
                searching = false
                query.clearText()
                model.setQuery(null)
            },
            onSelectMode = { incognito -> if (incognito != key.incognito) navigator.openTabs(incognito) },
            onOpen = navigator::showTab,
            onClose = model::close,
            onMove = model::move,
            onRename = { groupId -> navigator.openDialog(RenameGroupKey(groupId)) },
            onUngroup = model::ungroup,
            onCloseGroup = model::closeGroup,
            onUndoGrouping = model::undoGrouping,
            onAskSkim = navigator::openAskTabs,
            onNewIncognitoTab = if (key.incognito) {
                null
            } else {
                {
                    val open = shell.tabs().state.value.tabs.any { it.incognito }
                    context.confirmIncognito(open) { navigator.openNewTab(incognito = true) }
                }
            },
            onCloseAll = {
                model.closeAll()
                if (key.incognito) navigator.replaceTop(TabsKey(incognito = false))
            },
            onNewTab = {
                if (key.incognito) {
                    val open = shell.tabs().state.value.tabs.any { it.incognito }
                    context.confirmIncognito(open) { navigator.openNewTab(incognito = true) }
                } else {
                    navigator.openNewTab(incognito = false)
                }
            },
        ),
        thumbnails = shell.thumbnails(),
    )
}

/**
 * The tab grid. [searchState] replaces the title while it is set. [thumbnails] supplies each
 * card's preview; a screenshot passes null and the cards stay empty.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabsScreen(
    state: TabsUiState,
    searchState: TextFieldState?,
    actions: TabsActions,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    thumbnails: ThumbnailStore? = null,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val belowGrid = navigationBar + ToolbarMetrics.BottomOffset + ToolbarMetrics.Height + 16.dp
    val searching = searchState != null
    var menuGroupId by remember { mutableStateOf<Long?>(null) }
    // The order a drop of the dragged tab would give. The ViewModel decides the order; this only
    // shows it until the drop has settled.
    var reorder by remember { mutableStateOf<ReorderPreview?>(null) }
    val sections = remember(state.sections, reorder) { previewSections(state.sections, reorder) }
    val indexInGroup = remember(sections) {
        val map = HashMap<String, Int>()
        sections.forEach { section -> section.tabs.forEachIndexed { index, card -> map[card.id] = index } }
        return@remember { id: String -> map[id] }
    }
    val reduced = rememberReducedMotion()
    val tabKeys = remember(state.sections) {
        state.sections.flatMap { section -> section.tabs.map { it.id } }.toImmutableSet()
    }
    val groupOf = remember(state.sections) {
        val map = HashMap<String, Long?>()
        state.sections.forEach { section -> section.tabs.forEach { map[it.id] = section.group?.id } }
        return@remember { id: String -> map[id] }
    }
    LaunchedEffect(tabKeys) {
        // A tab closed while it was dragged takes its preview with it.
        if (reorder?.tabId?.let { it !in tabKeys } == true) reorder = null
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (searchState == null) {
                TitleRow(state, actions.onSearch)
            } else {
                SearchRow(
                    state = searchState,
                    placeholder = stringResource(
                        if (state.incognito) R.string.cd_search_incognito_tabs else R.string.cd_search_tabs,
                    ),
                    onClose = actions.onCloseSearch,
                )
            }
            ConnectedToggleGroup(
                options = persistentListOf(
                    stringResource(R.string.tabs_segment_open),
                    stringResource(R.string.tabs_segment_incognito),
                ),
                selected = if (state.incognito) 1 else 0,
                onSelect = { index -> actions.onSelectMode(index == 1) },
                contentDescription = stringResource(R.string.cd_tab_list),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = belowGrid),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (!searching && state.incognito) {
                    item(key = "incognito-banner", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.padding(bottom = CARD_GAP)) { IncognitoBanner() }
                    }
                } else if (!searching && state.grouping != null) {
                    item(key = "grouping-banner", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.padding(bottom = CARD_GAP)) {
                            GroupingBanner(state.grouping, actions.onUndoGrouping)
                        }
                    }
                }
                if (state.empty && !searching) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyTabs() }
                }
                sections.forEachIndexed { sectionIndex, section ->
                    if (section.showHeader) {
                        item(key = headerKey(section), span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                section = section,
                                menuOpen = menuGroupId != null && menuGroupId == section.group?.id,
                                onMenu = { menuGroupId = section.group?.id },
                                onDismissMenu = { menuGroupId = null },
                                onRename = { section.group?.let { actions.onRename(it.id) } },
                                onUngroup = { section.group?.let { actions.onUngroup(it.id) } },
                                onCloseGroup = { section.group?.let { actions.onCloseGroup(it.id) } },
                                modifier = Modifier.padding(
                                    top = if (sectionIndex == 0) 0.dp else SECTION_GAP,
                                    bottom = HEADER_GAP,
                                ),
                            )
                        }
                    }
                    val lastRow = section.tabs.lastIndex / GRID_COLUMNS
                    section.tabs.forEachIndexed { index, card ->
                        val row = index / GRID_COLUMNS
                        item(key = card.id) {
                            // The dragged card follows the finger itself; the others move by the
                            // grid's placement, at once with Remove animations.
                            val placed = reorder?.tabId != card.id && !reduced
                            SwipeCard(
                                card = card,
                                preview = previewOf(card.id, thumbnails),
                                incognito = state.incognito,
                                reorderEnabled = state.query == null,
                                gridState = gridState,
                                tabKeys = tabKeys,
                                groupOf = groupOf,
                                indexInGroup = indexInGroup,
                                onPreview = { index ->
                                    when {
                                        index != null -> reorder = ReorderPreview(card.id, index)
                                        // Another card may have been picked up while this one settled.
                                        reorder?.tabId == card.id -> reorder = null
                                    }
                                },
                                onOpen = { actions.onOpen(card.id) },
                                onClose = { actions.onClose(card.id) },
                                onMove = { index -> actions.onMove(card.id, index) },
                                modifier = Modifier
                                    .padding(
                                        top = if (row == 0 && !section.showHeader && sectionIndex > 0) SECTION_GAP else 0.dp,
                                        bottom = if (row == lastRow) 0.dp else CARD_GAP,
                                    )
                                    .testTag("tab-card")
                                    .then(if (placed) Modifier.animateItem() else Modifier.animateItem(placementSpec = null)),
                            )
                        }
                    }
                }
            }
        }
        TabSwitcherToolbar(
            incognito = state.incognito,
            aiEnabled = state.aiEnabled,
            onAskSkim = actions.onAskSkim,
            onNewIncognitoTab = actions.onNewIncognitoTab,
            onCloseAll = actions.onCloseAll,
            onNewTab = actions.onNewTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ToolbarMetrics.BottomOffset),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TitleRow(state: TabsUiState, onSearch: () -> Unit) {
    val title = if (state.incognito) {
        pluralStringResource(R.plurals.incognito_tabs_title, state.count, state.count)
    } else {
        pluralStringResource(R.plurals.tabs_title, state.count, state.count)
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onSearch) {
            Icon(
                painterResource(R.drawable.ic_search),
                stringResource(if (state.incognito) R.string.cd_search_incognito_tabs else R.string.cd_search_tabs),
            )
        }
    }
}

@Composable
private fun SearchRow(state: TextFieldState, placeholder: String, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(onBack = onClose)
    LaunchedEffect(state) { focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_close_search))
        }
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f).focusRequester(focus),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { keyboard?.hide() },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorator = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
        if (state.text.isNotEmpty()) {
            IconButton(onClick = { state.clearText() }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_clear_text))
            }
        }
    }
}

@Composable
private fun GroupingBanner(banner: GroupingBannerState, onUndo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.tertiaryContainer)
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_auto_awesome_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.onTertiaryContainer,
        )
        Text(
            text = stringResource(R.string.tabs_grouped_banner),
            modifier = Modifier.weight(1f),
            color = colors.onTertiaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (banner.undoEnabled) {
            TextButton(onClick = onUndo) {
                Text(
                    text = stringResource(R.string.action_undo),
                    color = colors.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@Composable
private fun IncognitoBanner() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_visibility_off),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.incognito_tabs_banner),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(
    section: TabSection,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
    onCloseGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = section.group?.title ?: stringResource(R.string.tabs_group_other)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.testTag("tab-section-header").fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CountBadge(section.tabs.size)
        Box(Modifier.weight(1f))
        if (section.group != null) {
            Box {
                if (menuOpen) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.12f)),
                    )
                }
                CompactIconButton(
                    icon = R.drawable.ic_more_vert,
                    contentDescription = stringResource(R.string.cd_group_options),
                    onClick = onMenu,
                    iconSize = 24.dp,
                    visualSize = 40.dp,
                )
                TabGroupMenu(
                    expanded = menuOpen,
                    groupTitle = title,
                    tabCount = section.tabs.size,
                    onRename = onRename,
                    onUngroup = onUngroup,
                    onClose = onCloseGroup,
                    onDismiss = onDismissMenu,
                )
            }
        }
    }
}

/**
 * A tab card that closes when swiped sideways past [SwipeCloseFraction] of its width. While it is
 * dragged, the space it leaves shows a close mark on errorContainer; at rest nothing is drawn
 * behind it. Passing the point where a release closes the tab gives a threshold haptic, each time
 * the drag passes it. A card picked up for reorder is drawn above the others.
 */
@Composable
private fun SwipeCard(
    card: TabCardState,
    preview: ImageBitmap?,
    incognito: Boolean,
    reorderEnabled: Boolean,
    gridState: LazyGridState,
    tabKeys: ImmutableSet<String>,
    groupOf: (String) -> Long?,
    indexInGroup: (String) -> Int?,
    onPreview: (Int?) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) false else {
                onClose()
                true
            }
        },
        positionalThreshold = { distance -> distance * SwipeCloseFraction },
    )
    var width by remember { mutableIntStateOf(0) }
    var lifted by remember { mutableStateOf(false) }
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    LaunchedEffect(dismissState) {
        snapshotFlow { width > 0 && abs(dismissState.requireOffset()) >= width * SwipeCloseFraction }
            .distinctUntilChanged()
            .collect { closes -> if (closes) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeCloseBackground(dismissState) },
        modifier = modifier
            .onSizeChanged { width = it.width }
            .zIndex(if (lifted) 1f else 0f),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        TabCard(
            title = card.title.ifBlank { stringResource(R.string.new_tab) },
            letter = card.letter,
            host = card.host,
            preview = preview,
            isCurrent = card.isCurrent,
            hasSkimChat = card.hasSkimChat,
            incognito = incognito,
            onClick = onOpen,
            onClose = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .tabDragReorder(
                    itemKey = card.id,
                    groupId = groupOf(card.id),
                    gridState = gridState,
                    enabled = reorderEnabled && !incognito,
                    tabKeys = tabKeys,
                    groupOf = groupOf,
                    indexInGroup = indexInGroup,
                    onPreview = onPreview,
                    onMove = onMove,
                    onLiftChange = { lifted = it },
                ),
        )
    }
}

/**
 * What a swiped card uncovers: errorContainer in the card's shape with the close mark in
 * onErrorContainer on the side the card leaves. Decoration only: the card's Close tab button is
 * the accessible way to close it. Nothing is drawn while the card is at rest.
 */
@Composable
private fun SwipeCloseBackground(state: SwipeToDismissBoxState) {
    val direction by remember(state) { derivedStateOf { state.dismissDirection } }
    if (direction == SwipeToDismissBoxValue.Settled) return
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.errorContainer, TabCardShape)
            .padding(horizontal = SwipeIconInset),
        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            painterResource(R.drawable.ic_close),
            contentDescription = null,
            tint = colors.onErrorContainer,
        )
    }
}

@Composable
private fun previewOf(tabId: String, store: ThumbnailStore?): ImageBitmap? {
    if (store == null) return null
    val bitmap by store.observe(tabId).collectAsStateWithLifecycle()
    return bitmap
}

@Composable
private fun EmptyTabs() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tabs_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun headerKey(section: TabSection): String = "header-${section.group?.id ?: "other"}"

private const val GRID_COLUMNS = 2

/** How far across its width a card is swiped before a release closes it. */
private const val SwipeCloseFraction = 0.4f
private val SwipeIconInset = 24.dp
private val CARD_GAP = 12.dp
private val HEADER_GAP = 8.dp
private val SECTION_GAP = 16.dp

private fun topBanner(state: TabsUiState, searching: Boolean): Boolean =
    !searching && (state.incognito || state.grouping != null)

/** The grid index of the header of the section that contains [tabId], or of its first card. */
internal fun focusHeaderIndex(state: TabsUiState, tabId: String, showTopBanner: Boolean): Int? {
    var index = if (showTopBanner) 1 else 0
    for (section in state.sections) {
        if (section.tabs.any { it.id == tabId }) return index
        index += (if (section.showHeader) 1 else 0) + section.tabs.size
    }
    return null
}
