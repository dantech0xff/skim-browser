package com.dantech.skimbrowser.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.BOARD_STATUS_BAR_HEIGHT
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lists, large titles and connected groups where their boards draw them. The large-title goldens
 * are whole screens (412 by 915 dp, status bar included) with the content of 12, 04c, 13 and 14, so
 * they line up with the board PNGs; the other goldens are horizontal strips of a board, starting at
 * the y each test names, so a crop of the PNG at that y compares directly.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ListsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings() = captureScreen("large-title-12") { SettingsScreen(it) }

    @Test
    fun skimHistory() = captureScreen("large-title-04c") { SkimHistoryScreen(it) }

    @Test
    fun skimHistoryInTheDarkTheme() = captureScreen("large-title-04c-dark", dark = true) { SkimHistoryScreen(it) }

    @Test
    fun history() = captureScreen("large-title-13") { HistoryScreen(it) }

    @Test
    fun bookmarks() = captureScreen("large-title-14") { BookmarksScreen(it) }

    @Test
    fun historyScrolledSoTheTitleCollapses() = captureCollapsed("large-title-13-collapsed", dark = false)

    @Test
    fun historyScrolledSoTheTitleCollapsesInTheDarkTheme() = captureCollapsed("large-title-13-collapsed-dark", dark = true)

    @Test
    fun searchingReplacesTheTitle() = captureScreen("large-title-search") { SearchingHistoryScreen(query = "heat pump", modifier = it) }

    @Test
    fun searchingReplacesTheTitleInTheDarkTheme() = captureScreen("large-title-search-dark", dark = true) {
        SearchingHistoryScreen(query = "heat pump", modifier = it)
    }

    @Test
    fun anEmptySearchShowsItsPlaceholder() = captureScreen("large-title-search-empty") { SearchingHistoryScreen(query = "", modifier = it) }

    /** The suggestion groups of 04b, from y 96 (under the search field) to 460. */
    @Test
    fun suggestionGroups() = captureStrip("segmented-list-04b", height = 364) {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.fillMaxSize().background(colors.surfaceContainerLow).padding(top = 8.dp)) {
            SegmentedColumn {
                SkimListItem(
                    headline = "heat pump",
                    supporting = stringResource(R.string.ask_skim),
                    index = 0,
                    count = 1,
                    onClick = {},
                    leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
                    supportingColor = colors.tertiary,
                    supportingWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            SegmentedColumn {
                listOf("heat pump vs furnace", "heat pump cost", "heat pump rebates 2026").forEachIndexed { index, query ->
                    SkimListItem(
                        headline = query,
                        index = index,
                        count = 3,
                        onClick = {},
                        leading = { IconInTile(R.drawable.ic_search) },
                        trailing = {
                            IconButton(onClick = {}) {
                                Icon(painterResource(R.drawable.ic_north_west), stringResource(R.string.cd_edit_search, query))
                            }
                        },
                        trailingIsIconButton = true,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SegmentedColumn {
                SkimListItem(
                    headline = "Why heat pumps are winning in cold climates",
                    supporting = "daylightjournal.com",
                    index = 0,
                    count = 1,
                    onClick = {},
                    singleLine = true,
                    leading = { IconInTile(R.drawable.ic_history) },
                )
            }
        }
    }

    /** A group with its middle item held down: the item rounds all four corners to 20 dp. */
    @Test
    fun aPressedItemRoundsItsCorners() {
        captureStrip("segmented-list-pressed", height = 260, capture = false) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 16.dp)) {
                SegmentedColumn { GeneralSettings() }
            }
        }
        composeRule.onNodeWithText("Default browser").performTouchInput { down(center) }
        composeRule.onNodeWithTag(FRAME).captureComponent("segmented-list-pressed")
        composeRule.onNodeWithText("Default browser").performTouchInput { up() }
    }

    /** The two static lists of 09c, from y 328 (under the hero) to 732. */
    @Test
    fun incognitoNewTabLists() = captureStrip("static-list-09c", height = 404, incognito = true) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // 09c keeps 32 dp above its first label, after the hero.
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
        }
    }

    /** Tab list of 09: the top 144 dp of the board, the group 88 dp down (the title row is not drawn here). */
    @Test
    fun tabListGroup() = captureStrip("connected-toggle-09", height = 144) { TabListStrip(selected = 0) }

    @Test
    fun tabListGroupInIncognito() = captureStrip("connected-toggle-09b", height = 144, incognito = true) {
        TabListStrip(selected = 1)
    }

    /** Font of 08b: the panel from y 751 to the bottom, the group 92 dp into it. */
    @Test
    fun fontGroup() = captureStrip("connected-toggle-08b", height = 164) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 24.dp),
        ) {
            // The group lays out 48 dp high around its 40 dp buttons.
            Spacer(Modifier.height(88.dp))
            ConnectedToggleGroup(
                options = persistentListOf(stringResource(R.string.reader_font_sans_serif), stringResource(R.string.reader_font_serif)),
                selected = 0,
                onSelect = {},
                contentDescription = stringResource(R.string.reader_font),
                fontFamilies = persistentListOf(null, FontFamily.Serif),
            )
        }
    }

    @Composable
    private fun TabListStrip(selected: Int) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // The status bar and the 64 dp title row; the group lays out 4 dp above its 40 dp buttons.
            Spacer(Modifier.height(BOARD_STATUS_BAR_HEIGHT + 60.dp))
            ConnectedToggleGroup(
                options = persistentListOf(stringResource(R.string.tabs_segment_open), stringResource(R.string.tabs_segment_incognito)),
                selected = selected,
                onSelect = {},
                contentDescription = stringResource(R.string.cd_tab_list),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    private fun captureScreen(name: String, dark: Boolean = false, screen: @Composable (Modifier) -> Unit) {
        composeRule.setSkimContent(dark = dark) { screen(Modifier.testTag(FRAME)) }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private fun captureCollapsed(name: String, dark: Boolean) {
        composeRule.setSkimContent(dark = dark) { HistoryScreen(Modifier.testTag(FRAME)) }
        composeRule.onNodeWithTag(FRAME).performTouchInput {
            swipeUp(startY = bottom - 200f, endY = bottom - 900f)
        }
        composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private fun captureStrip(
        name: String,
        height: Int,
        incognito: Boolean = false,
        capture: Boolean = true,
        strip: @Composable () -> Unit,
    ) {
        composeRule.setSkimContent(incognito = incognito) {
            Box(Modifier.fillMaxWidth().height(height.dp).testTag(FRAME)) { strip() }
        }
        if (capture) composeRule.onNodeWithTag(FRAME).captureComponent(name)
        composeRule.assertA11y()
    }

    private companion object {
        const val FRAME = "frame"
    }
}

/** Settings (12). */
@Composable
private fun SettingsScreen(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    LargeTitleScaffold(title = stringResource(R.string.settings_title), onBack = {}, modifier = modifier) {
        section(R.string.settings_section_general, first = true) { GeneralSettings() }
        section(R.string.skim_ai) {
            SkimListItem(
                headline = stringResource(R.string.settings_ai_privacy),
                supporting = stringResource(R.string.settings_ai_privacy_summary_ask),
                index = 0,
                count = 1,
                onClick = {},
                leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
            )
        }
        section(R.string.settings_section_privacy) {
            SkimListItem(
                headline = stringResource(R.string.clear_browsing_data),
                supporting = stringResource(R.string.settings_clear_browsing_data_summary),
                index = 0,
                count = 1,
                onClick = {},
                leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
            )
        }
        section(R.string.settings_section_about) {
            listOf(
                R.drawable.ic_shield to R.string.settings_privacy_policy,
                R.drawable.ic_description to R.string.settings_terms_of_service,
                R.drawable.ic_code to R.string.settings_open_source_licenses,
            ).forEachIndexed { index, (icon, label) ->
                SkimListItem(
                    headline = stringResource(label),
                    index = index,
                    count = 4,
                    onClick = {},
                    leading = { IconTile(icon, colors.surfaceContainer, colors.onSurfaceVariant) },
                )
            }
            SkimListItem(
                headline = stringResource(R.string.settings_version),
                index = 3,
                count = 4,
                onClick = null,
                leading = { IconTile(R.drawable.ic_info, colors.surfaceContainer, colors.onSurfaceVariant) },
                trailing = {
                    Text(stringResource(R.string.settings_version_value, "1.0.0", 100), style = MaterialTheme.typography.bodyMedium)
                },
            )
        }
    }
}

/** The General group of 12: three two-line items. */
@Composable
private fun GeneralSettings() {
    val colors = MaterialTheme.colorScheme
    listOf(
        Triple(R.string.settings_search_engine, R.string.search_engine_google, R.drawable.ic_search),
        Triple(R.string.settings_default_browser, R.string.settings_default_browser_summary_is_default, R.drawable.ic_public),
        Triple(R.string.settings_theme, R.string.theme_system_default, R.drawable.ic_palette),
    ).forEachIndexed { index, (headline, supporting, icon) ->
        // Default browser is the one tile in primaryContainer.
        val container = if (index == 1) colors.primaryContainer else colors.secondaryContainer
        val content = if (index == 1) colors.onPrimaryContainer else colors.onSecondaryContainer
        SkimListItem(
            headline = stringResource(headline),
            supporting = stringResource(supporting),
            index = index,
            count = 3,
            onClick = {},
            leading = { IconTile(icon, container, content) },
        )
    }
}

/** Skim history (04c), without its footer. */
@Composable
private fun SkimHistoryScreen(modifier: Modifier) {
    LargeTitleScaffold(
        title = stringResource(R.string.skim_history_title),
        onBack = {},
        modifier = modifier,
        actions = {
            ActionButton(R.drawable.ic_search, R.string.cd_search_skim_history)
            ActionButton(R.drawable.ic_delete_sweep, R.string.cd_delete_all_skim_history)
        },
    ) {
        section(R.string.date_today, first = true) {
            SkimHistoryItem(
                headline = "Why heat pumps are winning in cold climates",
                supporting = pluralStringResource(R.plurals.skim_history_questions_about, 1, 1, "daylightjournal.com"),
                index = 0,
                count = 2,
            ) { FaviconLetterTile("D") }
            SkimHistoryItem(
                headline = pluralStringResource(R.plurals.home_summarize_tabs, 7, 7),
                supporting = pluralStringResource(R.plurals.skim_history_answers_across_tabs, 1, 1, 7),
                index = 1,
                count = 2,
            ) { SunnyMark(size = 40.dp, iconSize = 20.dp) }
        }
        section(R.string.date_yesterday) {
            SkimHistoryItem(
                headline = "Osaka night buses: a complete guide",
                supporting = stringResource(R.string.skim_history_summary_of, "tabi-note.jp"),
                index = 0,
                count = 2,
            ) { FaviconLetterTile("T") }
            SkimHistoryItem(
                headline = "Kotlin 2.2 release notes",
                supporting = pluralStringResource(R.plurals.skim_history_questions_about, 3, 3, "kotlinlang.org"),
                index = 1,
                count = 2,
            ) { FaviconLetterTile("K") }
        }
        section(R.string.date_earlier_this_week) {
            SkimHistoryItem(
                headline = "Heat pump rebates 2026",
                supporting = stringResource(R.string.skim_history_summary_of, "energysaver.example"),
                index = 0,
                count = 1,
            ) { FaviconLetterTile("E") }
        }
    }
}

@Composable
private fun SkimHistoryItem(
    headline: String,
    supporting: String,
    index: Int,
    count: Int,
    leading: @Composable () -> Unit,
) {
    SkimListItem(
        headline = headline,
        supporting = supporting,
        index = index,
        count = count,
        onClick = {},
        singleLine = true,
        leading = leading,
        trailing = { RemoveButton(R.string.cd_remove_from_skim_history) },
        trailingIsIconButton = true,
    )
}

/** History (13). */
@Composable
private fun HistoryScreen(modifier: Modifier) {
    LargeTitleScaffold(
        title = stringResource(R.string.history_title),
        onBack = {},
        modifier = modifier,
        actions = { ActionButton(R.drawable.ic_search, R.string.cd_search_history) },
    ) {
        item {
            val colors = MaterialTheme.colorScheme
            SegmentedColumn(Modifier.padding(top = 8.dp)) {
                SkimListItem(
                    headline = stringResource(R.string.clear_browsing_data),
                    index = 0,
                    count = 1,
                    onClick = {},
                    leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
                )
            }
        }
        section(R.string.date_today) { HistoryItems(HistoryToday) }
        section(R.string.date_yesterday) { HistoryItems(HistoryYesterday) }
    }
}

/** History while searching: the field replaces the title and the list shows the matches. */
@Composable
private fun SearchingHistoryScreen(query: String, modifier: Modifier) {
    LargeTitleScaffold(
        title = stringResource(R.string.history_title),
        onBack = {},
        modifier = modifier,
        searchState = rememberTextFieldState(query),
        searchPlaceholder = stringResource(R.string.cd_search_history),
    ) {
        section(R.string.date_today, first = true) { HistoryItems(HistoryToday.filter { "heat pump" in it.title.lowercase() }) }
    }
}

@Composable
private fun HistoryItems(entries: List<HistoryEntry>) {
    entries.forEachIndexed { index, (letter, title, host) ->
        SkimListItem(
            headline = title,
            supporting = host,
            index = index,
            count = entries.size,
            onClick = {},
            singleLine = true,
            leading = { FaviconLetterTile(letter) },
            trailing = { RemoveButton(R.string.cd_remove_from_history) },
            trailingIsIconButton = true,
        )
    }
}

/** Bookmarks (14). */
@Composable
private fun BookmarksScreen(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    LargeTitleScaffold(
        title = stringResource(R.string.bookmarks_title),
        onBack = {},
        modifier = modifier,
        actions = { ActionButton(R.drawable.ic_search, R.string.cd_search_bookmarks) },
    ) {
        section(R.string.bookmarks_section_folders, first = true) {
            listOf(
                stringResource(R.string.bookmarks_folder_mobile) to pluralStringResource(R.plurals.bookmarks_count, 3, 3),
                stringResource(R.string.bookmarks_folder_imported) to
                    pluralStringResource(R.plurals.bookmarks_imported_count_from, 24, 24, "bookmarks.html"),
            ).forEachIndexed { index, (name, summary) ->
                SkimListItem(
                    headline = name,
                    supporting = summary,
                    index = index,
                    count = 2,
                    onClick = {},
                    leading = {
                        IconTile(
                            R.drawable.ic_folder_filled,
                            colors.secondaryContainer,
                            colors.onSecondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        )
                    },
                )
            }
        }
        section(R.string.bookmarks_section_recently_added) {
            val recent = listOf(
                "Why heat pumps are winning in cold climates" to "daylightjournal.com",
                "Osaka night buses: a complete guide" to "tabi-note.jp",
                "Kotlin 2.2 release notes" to "kotlinlang.org",
            )
            recent.forEachIndexed { index, (title, host) ->
                SkimListItem(
                    headline = title,
                    supporting = host,
                    index = index,
                    count = recent.size,
                    onClick = {},
                    singleLine = true,
                    leading = { FaviconLetterTile(siteLetter(host, title = null), content = colors.onSurfaceVariant) },
                    trailing = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_bookmark_options))
                        }
                    },
                    trailingIsIconButton = true,
                )
            }
        }
    }
}

private fun LazyListScope.section(@StringRes label: Int, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    item { SectionLabel(stringResource(label), first) }
    item { SegmentedColumn(content = content) }
}

@Composable
private fun ActionButton(@DrawableRes icon: Int, @StringRes label: Int) {
    IconButton(onClick = {}) { Icon(painterResource(icon), stringResource(label)) }
}

/** The Remove control of 04c and 13: a 20 dp close icon in a 48 dp button. */
@Composable
private fun RemoveButton(@StringRes label: Int) {
    IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_close), stringResource(label), Modifier.size(20.dp)) }
}

/** An icon centered in the 40 dp leading cell, without a tile (04b). */
@Composable
private fun IconInTile(@DrawableRes icon: Int) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null) }
}

/** A history row of 13, with the letter the board draws on its tile. */
private data class HistoryEntry(val letter: String, val title: String, val host: String)

private val HistoryToday = listOf(
    HistoryEntry("D", "Why heat pumps are winning in cold climates", "daylightjournal.com"),
    HistoryEntry("H", "Heat pump sizing calculator", "heatcalc.example"),
    HistoryEntry("R", "Installer reviews near me", "localreviews.example"),
    HistoryEntry("E", "Heat pump rebates 2026", "energysaver.example"),
)

private val HistoryYesterday = listOf(
    HistoryEntry("T", "Osaka night buses: a complete guide", "tabi-note.jp"),
    HistoryEntry("N", "Namba hotels, 2 nights", "staysearch.example"),
    HistoryEntry("K", "Kotlin 2.2 release notes", "kotlinlang.org"),
)
