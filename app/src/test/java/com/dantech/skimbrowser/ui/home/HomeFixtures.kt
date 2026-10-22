package com.dantech.skimbrowser.ui.home

import androidx.compose.runtime.Composable
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabRuntime
import com.dantech.skimbrowser.data.db.entity.ShortcutEntity
import com.dantech.skimbrowser.ui.components.BrowserToolbar
import com.dantech.skimbrowser.ui.shell.TabAiFab
import com.dantech.skimbrowser.ui.text.UiText

/** The data Home (04) and the incognito New tab page (09c) are drawn with on their boards. */
object HomeFixtures {

    /** The seven shortcuts of 04, in tile order. */
    val Shortcuts: List<ShortcutEntity> = listOf(
        "Wikipedia" to "https://www.wikipedia.org",
        "GitHub" to "https://github.com",
        "YouTube" to "https://www.youtube.com",
        "Hacker News" to "https://news.ycombinator.com",
        "Reddit" to "https://www.reddit.com",
        "Medium" to "https://medium.com",
        "Stack Overflow" to "https://stackoverflow.com",
    ).mapIndexed { index, (name, url) -> ShortcutEntity(id = index + 1L, name = name, url = url, position = index) }

    /** The two Continue with Skim cards of 04. */
    val ContinueCards: List<ContinueCard> = listOf(
        ContinueCard(
            chatId = 11,
            host = "daylightjournal.com",
            letter = "D",
            timeText = UiText.Plural(R.plurals.time_hours_ago, 2),
            title = "Why heat pumps are winning in cold climates",
            snippet = "Cold-climate models now hold their efficiency well below freezing; the article puts typical " +
                "savings at \$300 to \$900 a year when replacing oil heat.",
        ),
        ContinueCard(
            chatId = 12,
            host = "tabi-note.jp",
            letter = "T",
            timeText = UiText.Res(R.string.date_yesterday),
            title = "Osaka night buses: a complete guide",
            snippet = "Three routes run after midnight from Namba and Umeda; the guide covers fares, where to buy " +
                "tickets and which stops are closest to the hotels.",
        ),
    )

    /** Home as 04 draws it: seven tabs to summarize, seven shortcuts, two cards and the mic. */
    val Board = HomeUiState(eligibleTabCount = 7, shortcuts = Shortcuts, continueChats = ContinueCards, voiceAvailable = true)

    /** The first Home, before anything is browsed. */
    val FirstHome = HomeUiState(voiceAvailable = true)

    /** A tab on its New tab page. */
    fun newTabPage(id: String = "tab-1", incognito: Boolean = false) = Tab(
        id = id,
        incognito = incognito,
        url = null,
        title = "",
        groupId = null,
        position = 0,
        createdAt = 1L,
        lastAccessedAt = 1L,
    )

    /** The floating toolbar of [tab]'s New tab page, with [tabCount] tabs of its kind. */
    @Composable
    fun Toolbar(tab: Tab, tabCount: Int) {
        BrowserToolbar(
            canGoBack = false,
            canGoForward = false,
            tabCount = tabCount,
            incognito = tab.incognito,
            onBack = {},
            onForward = {},
            onTabs = {},
            onMore = {},
            aiFab = { TabAiFab(tab, TabRuntime(), onClick = {}) },
        )
    }

    /** Actions that record what was asked of Home. */
    class Recorder {
        val calls = mutableListOf<String>()

        val actions = HomeActions(
            onSearch = { calls += "search" },
            onVoice = { calls += "voice" },
            onSummarizeTabs = { prompt -> calls += "summarize:$prompt" },
            onOpenShortcut = { shortcut -> calls += "open:${shortcut.name}" },
            onAddShortcut = { calls += "add" },
            onEditShortcut = { shortcut -> calls += "edit:${shortcut.id}" },
            onRemoveShortcut = { shortcut -> calls += "remove:${shortcut.id}" },
            onOpenChat = { chatId -> calls += "chat:$chatId" },
            onSeeAllChats = { calls += "see all" },
        )
    }
}
