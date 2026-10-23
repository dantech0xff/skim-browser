package com.dantech.skimbrowser.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.dantech.skimbrowser.domain.tab.HitTarget
import com.dantech.skimbrowser.ui.snackbar.SnackbarPlacement
import kotlinx.serialization.Serializable

/**
 * A destination of the app. Every key carries what its screen needs as explicit parameters, is
 * saved with the back stack (so open sheets and dialogs come back after process death) and is
 * listed in [skimNavConfig]; nothing about a key is found by reflection.
 *
 * Screens implement this interface directly; sheets implement [SheetKey] and dialogs [DialogKey],
 * which show over the screen below them.
 */
@Serializable
sealed interface SkimKey : NavKey {
    /** Where snackbars sit while this screen is on top. */
    val placement: SnackbarPlacement get() = SnackbarPlacement.Bottom

    /** The tab the destination shows or acts on; a key whose tab is gone is dropped from the back stack. */
    val tabId: String? get() = null
}

/** A destination shown as a bottom sheet over the screen below it. */
@Serializable
sealed interface SheetKey : SkimKey

/** A destination shown as a dialog over the screen below it. */
@Serializable
sealed interface DialogKey : SkimKey

// Screens

/** 01 Welcome. */
@Serializable
data object WelcomeKey : SkimKey

/** 03 Default browser and import. */
@Serializable
data object SetupKey : SkimKey

/** The current tab: Home (04), a page (05), Find (05c), Incognito new tab (09c) or Page failed to load (11). */
@Serializable
data object BrowserKey : SkimKey {
    override val placement: SnackbarPlacement get() = SnackbarPlacement.AboveToolbar
}

/** 08 Reader view of [tabId]. */
@Serializable
data class ReaderKey(override val tabId: String) : SkimKey {
    override val placement: SnackbarPlacement get() = SnackbarPlacement.AboveToolbar
}

/** 09 Tabs, or 09b Incognito tabs; [focusTabId] is the tab the grid scrolls to. */
@Serializable
data class TabsKey(val incognito: Boolean, val focusTabId: String? = null) : SkimKey {
    override val placement: SnackbarPlacement get() = SnackbarPlacement.AboveToolbar
}

/** 12 Settings. */
@Serializable
data object SettingsKey : SkimKey

/** 10 AI and privacy settings. */
@Serializable
data object AiPrivacyKey : SkimKey

/** 13 History. */
@Serializable
data object HistoryKey : SkimKey

/** 14 Bookmarks, at the top level or in folder [folderId]. */
@Serializable
data class BookmarksKey(val folderId: Long? = null) : SkimKey

/** 04c Skim history. */
@Serializable
data object SkimHistoryKey : SkimKey

/** Open-source licenses (12). */
@Serializable
data object LicensesKey : SkimKey

// Sheets

/** 05b More menu of [tabId]. */
@Serializable
data class MoreMenuKey(override val tabId: String) : SheetKey

/** 05d Link or Image options for what a long-press on [tabId]'s page hit. */
@Serializable
data class LinkOptionsKey(override val tabId: String, val target: HitTarget) : SheetKey

/** The site information sheet of [tabId]'s page (15). */
@Serializable
data class SiteInfoKey(override val tabId: String) : SheetKey

/** 06 Ask about this page: of [tabId], or the saved chat [savedChatId]; [fromReader] reads the article. */
@Serializable
data class AskPageKey(
    override val tabId: String? = null,
    val savedChatId: Long? = null,
    val fromReader: Boolean = false,
    /** The host allowed when the sheet opened. Null on a saved chat and on a restored stack. */
    val approvedHost: String? = null,
) : SheetKey

/** 06b Ask Skim across the open tabs: with [prompt] already sent, or the saved chat [savedChatId]. */
@Serializable
data class AskTabsKey(val prompt: String? = null, val savedChatId: Long? = null) : SheetKey

// Dialogs

/** 06d AI permission, the first time Skim reads [tabId]'s site. */
@Serializable
data class AiPermissionKey(override val tabId: String, val fromReader: Boolean = false) : DialogKey

/** Add shortcut (15), or Edit shortcut for [shortcutId]. */
@Serializable
data class ShortcutDialogKey(val shortcutId: Long? = null) : DialogKey

/** Search engine choice (15). */
@Serializable
data object SearchEngineDialogKey : DialogKey

/** Theme choice (15). */
@Serializable
data object ThemeDialogKey : DialogKey

/** Clear browsing data (15). */
@Serializable
data object ClearBrowsingDataKey : DialogKey

/** Delete all AI data (15). */
@Serializable
data object DeleteAiDataKey : DialogKey

/** Clear Skim history (15). */
@Serializable
data object ClearSkimHistoryKey : DialogKey

/** Rename group (15) for tab group [groupId]. */
@Serializable
data class RenameGroupKey(val groupId: Long) : DialogKey

/** Edit bookmark (15) for [bookmarkId]. */
@Serializable
data class EditBookmarkKey(val bookmarkId: Long) : DialogKey

/** Move to folder (15) for [bookmarkId]. */
@Serializable
data class MoveToFolderKey(val bookmarkId: Long) : DialogKey

/** True for a sheet or a dialog, which shows over the screen below it. */
val SkimKey.isOverlay: Boolean get() = this is SheetKey || this is DialogKey

/**
 * What is on screen for [keys]: the top screen and every sheet or dialog over it, bottom to top.
 * Keys below the top screen are not shown.
 */
fun visibleKeys(keys: List<NavKey>): List<SkimKey> {
    val skimKeys = keys.filterIsInstance<SkimKey>()
    val screen = skimKeys.indexOfLast { !it.isOverlay }
    return if (screen < 0) skimKeys else skimKeys.subList(screen, skimKeys.size)
}

/** The top screen of [keys], under any sheet or dialog; null when there is none. */
fun topScreen(keys: List<NavKey>): SkimKey? = keys.lastOrNull { it is SkimKey && !it.isOverlay } as SkimKey?
