package com.dantech.skimbrowser.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.ui.components.AddShortcutTile
import com.dantech.skimbrowser.ui.components.AiHeroCard
import com.dantech.skimbrowser.ui.components.FaviconLetterTile
import com.dantech.skimbrowser.ui.components.SearchBarField
import com.dantech.skimbrowser.ui.components.ShortcutTile
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.ToolbarMetrics
import com.dantech.skimbrowser.ui.components.segmentedShapes
import com.dantech.skimbrowser.ui.components.siteLetter
import com.dantech.skimbrowser.ui.text.asString

/** What the controls of Home (04) do. */
@Immutable
data class HomeActions(
    /** The search bar: opens Search & suggestions (04b) with the keyboard up. */
    val onSearch: () -> Unit,
    /** The mic of the search bar, shown while an app takes speech input. */
    val onVoice: () -> Unit,
    /** "Summarize my N open tabs", with that text as the prompt. */
    val onSummarizeTabs: (prompt: String) -> Unit,
    val onOpenShortcut: (ShortcutEntity) -> Unit,
    /** The Add tile after the shortcuts. */
    val onAddShortcut: () -> Unit,
    /** Edit shortcut in a shortcut's menu. */
    val onEditShortcut: (ShortcutEntity) -> Unit,
    /** Remove in a shortcut's menu. */
    val onRemoveShortcut: (ShortcutEntity) -> Unit,
    /** A Continue with Skim card. */
    val onOpenChat: (chatId: Long) -> Unit,
    /** See all, beside Continue with Skim. */
    val onSeeAllChats: () -> Unit,
)

/**
 * Home, the New tab page of a normal tab (04): the Sunny mark and the search bar, the AI hero card
 * "Summarize my N open tabs", Shortcuts with the Add tile, and Continue with Skim, over the floating
 * [toolbar].
 *
 * The first Home has none of the browsing parts: the hero card shows only while N is above 0,
 * Continue with Skim only while there is a page chat, and Shortcuts holds only the Add tile until
 * one is added. A long press on a shortcut opens its menu (15) under the tile, which turns
 * secondaryContainer meanwhile.
 *
 * The content scrolls, with room at its end for the toolbar, the 24 dp under it and the navigation
 * bar, so its last part can always be scrolled clear of the toolbar.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = ScreenMargin,
                        top = TopPadding,
                        end = ScreenMargin,
                        bottom = ToolbarMetrics.Height + ToolbarMetrics.BottomOffset + navigationBar,
                    ),
                ),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LogoGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SunnyMark(size = 64.dp, iconSize = 32.dp, contentDescription = stringResource(R.string.app_name))
                SearchBarField(
                    incognito = false,
                    onClick = actions.onSearch,
                    onVoice = if (state.voiceAvailable) actions.onVoice else null,
                )
            }
            if (state.showsHero) {
                Spacer(Modifier.height(SectionGap))
                val prompt = pluralStringResource(R.plurals.home_summarize_tabs, state.eligibleTabCount, state.eligibleTabCount)
                AiHeroCard(
                    title = prompt,
                    body = stringResource(R.string.home_summarize_tabs_body),
                    onClick = { actions.onSummarizeTabs(prompt) },
                )
            }
            Spacer(Modifier.height(SectionGap))
            SectionTitle(stringResource(R.string.home_shortcuts))
            Spacer(Modifier.height(12.dp))
            ShortcutGrid(
                shortcuts = state.shortcuts,
                onOpen = actions.onOpenShortcut,
                onEdit = actions.onEditShortcut,
                onRemove = actions.onRemoveShortcut,
                onAdd = actions.onAddShortcut,
            )
            if (state.showsContinue) {
                // See all keeps a 48 dp touch target around its 40 dp shape, so its row starts 4 dp
                // higher and ends 4 dp lower than drawn and the cards stay where 04 draws them.
                Spacer(Modifier.height(SectionGap - ButtonTargetInset))
                ContinueWithSkim(state.continueChats, actions.onOpenChat, actions.onSeeAllChats)
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
 * The shortcut tiles of Home in four columns 8 dp apart, rows 16 dp apart, each with its name
 * under it, then the Add tile. The shape of a tile follows its place in the list.
 */
@Composable
internal fun ShortcutGrid(
    shortcuts: List<ShortcutEntity>,
    onOpen: (ShortcutEntity) -> Unit,
    onEdit: (ShortcutEntity) -> Unit,
    onRemove: (ShortcutEntity) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells: List<ShortcutEntity?> = shortcuts + null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GridRowGap)) {
        cells.chunked(GridColumns).forEachIndexed { row, rowCells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GridColumnGap)) {
                rowCells.forEachIndexed { column, shortcut ->
                    val cellModifier = Modifier.weight(1f)
                    if (shortcut == null) {
                        AddCell(onAdd, cellModifier)
                    } else {
                        // Keyed by shortcut, so an open menu stays with its shortcut when others move.
                        key(shortcut.id) {
                            ShortcutCell(
                                shortcut = shortcut,
                                position = row * GridColumns + column,
                                onOpen = { onOpen(shortcut) },
                                onEdit = { onEdit(shortcut) },
                                onRemove = { onRemove(shortcut) },
                                modifier = cellModifier,
                            )
                        }
                    }
                }
                // Keeps the cells of a short last row as wide as the others.
                repeat(GridColumns - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A shortcut: its tile and name. A tap opens it; a long press opens its menu under the tile. */
@Composable
private fun ShortcutCell(
    shortcut: ShortcutEntity,
    position: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        CellColumn(
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onLongClick = { menuOpen = true },
                onClick = onOpen,
            ),
        ) {
            ShortcutTile(
                letter = siteLetter(host = null, title = shortcut.name),
                position = position,
                container = if (menuOpen) colors.secondaryContainer else colors.surfaceContainerHighest,
            )
            CellLabel(shortcut.name)
        }
        ShortcutMenu(
            name = shortcut.name,
            expanded = menuOpen,
            onEdit = onEdit,
            onRemove = onRemove,
            onDismiss = { menuOpen = false },
        )
    }
}

/** The Add tile and its label: opens the Add shortcut dialog. */
@Composable
private fun AddCell(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    CellColumn(modifier.clickable(role = Role.Button, onClick = onAdd)) {
        AddShortcutTile()
        CellLabel(stringResource(R.string.home_add_shortcut))
    }
}

/** A cell of the grid: the 64 dp tile centered over its name, 8 dp apart, with a rounded press. */
@Composable
private fun CellColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        // Clipped before the click, so the press ripple has the rounded corners of the cell.
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).then(modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/** A name under a tile: bodySmall, up to two lines like "Stack Overflow" on 04, then an ellipsis. */
@Composable
private fun CellLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Continue with Skim: the header with See all, then the newest page chats as one segmented group of
 * cards on surfaceContainerLow (20 dp outer and 4 dp inner corners, 2 dp apart).
 */
@Composable
private fun ContinueWithSkim(cards: List<ContinueCard>, onOpenChat: (Long) -> Unit, onSeeAll: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.home_continue_with_skim), Modifier.weight(1f))
            // The label ends at the content edge, as drawn: the button's 12 dp padding reaches past it.
            SkimButton(
                text = stringResource(R.string.action_see_all),
                onClick = onSeeAll,
                style = SkimButtonStyle.Text,
                modifier = Modifier.offset(x = TextButtonPadding),
            )
        }
        Spacer(Modifier.height(ContinueHeaderGap - ButtonTargetInset))
        Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
            cards.forEachIndexed { index, card ->
                ContinueCardItem(card, index, cards.size, onClick = { onOpenChat(card.chatId) })
            }
        }
    }
}

/**
 * A Continue with Skim card: the site's letter tile, host and time, then the page title in
 * titleMedium and the chat's summary line in two lines at most. The card is one button.
 */
@Composable
private fun ContinueCardItem(card: ContinueCard, index: Int, count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shape = remember(index, count) { segmentedShapes(index, count).shape }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.role = Role.Button },
        shape = shape,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FaviconLetterTile(
                    letter = card.letter,
                    size = 24.dp,
                    textStyle = typography.labelMediumEmphasized,
                    content = colors.onSurfaceVariant,
                )
                Text(
                    card.host,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant,
                    style = typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(card.timeText.asString(), color = colors.onSurfaceVariant, style = typography.bodySmall, maxLines = 1)
            }
            Text(card.title, style = typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.snippet.isNotEmpty()) {
                Text(card.snippet, color = colors.onSurfaceVariant, style = typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A section title of Home: titleMediumEmphasized in onSurface, a heading for accessibility. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMediumEmphasized,
    )
}

private val ScreenMargin = 16.dp
private val TopPadding = 24.dp

/** Between the Sunny mark and the search bar. */
private val LogoGap = 20.dp

/** Between the parts of Home. */
private val SectionGap = 24.dp
private const val GridColumns = 4
private val GridColumnGap = 8.dp
private val GridRowGap = 16.dp
private val ContinueHeaderGap = 8.dp
private val CardGap = 2.dp
private val CardPadding = 16.dp

/** A 40 dp button lays out 48 dp high: 4 dp above and below its shape. */
private val ButtonTargetInset: Dp = 4.dp

/** The horizontal padding of a text button, which reaches past the label's end. */
private val TextButtonPadding = 12.dp
