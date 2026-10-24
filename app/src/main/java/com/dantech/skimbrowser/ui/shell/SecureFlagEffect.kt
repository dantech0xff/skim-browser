package com.dantech.skimbrowser.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.domain.tab.TabsState
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.SkimKey
import com.dantech.skimbrowser.ui.navigation.TabsKey
import com.dantech.skimbrowser.ui.navigation.visibleKeys
import java.util.concurrent.atomic.AtomicLong

/**
 * The owner of FLAG_SECURE for what is on screen. The navigator takes it right before a change
 * that shows incognito content; [SecureFlagEffect] settles it after every such change.
 */
internal const val INCOGNITO_SURFACE_OWNER = "incognito-surface"

/** The owner that keeps the Recents preview hidden while an incognito tab is open. */
private const val INCOGNITO_TABS_OWNER = "incognito-tabs"

/**
 * True when [key] shows incognito content with the open [tabs]: the browser on an incognito tab,
 * Incognito tabs (09b), or any screen, sheet or dialog of an incognito tab.
 *
 * A key is pushed while its tab is open, and a page can close its own pop-up while a screen of it
 * is up, so a key whose tab is no longer open still counts as incognito: what its screen shows may
 * be that tab's, until the key leaves the back stack.
 */
fun showsIncognito(key: SkimKey, tabs: TabsState): Boolean = when (key) {
    BrowserKey -> tabs.currentTab?.incognito == true
    is TabsKey -> key.incognito
    else -> key.tabId?.let { tabs.tab(it)?.incognito ?: true } == true
}

/**
 * Whether the window must be secure for the back stack [keys] and the open [tabs]: something on
 * screen shows incognito content. On screen are the top screen and every sheet or dialog over it,
 * so a sheet of an incognito tab counts even over a screen that is not incognito.
 */
fun secureTarget(keys: List<NavKey>, tabs: TabsState): Boolean = visibleKeys(keys).any { showsIncognito(it, tabs) }

/**
 * Holds FLAG_SECURE while [target] (from [secureTarget]) is true, and keeps the Recents preview
 * hidden while [hasIncognitoTabs] (API 33 and later; FLAG_SECURE alone hides it before).
 *
 * The flag is set while the composition that first shows incognito content is applied, before its
 * frame is drawn; the navigator has usually set it already. [armed] is the navigator's
 * [secureArming][com.dantech.skimbrowser.ui.navigation.SkimNavigator.secureArming]: every change
 * it armed the flag for is settled here, so one that did not end on incognito content lets it go.
 * Letting go never uncovers a frame still on screen: incognito content that is animating away holds
 * the flag through [rememberSecureEntryDecorator], and the controller clears it only once the
 * current frame is done.
 */
@Composable
fun SecureFlagEffect(target: Boolean, hasIncognitoTabs: Boolean, armed: Int, secure: WindowPrivacy) {
    DisposableEffect(secure, target, armed) {
        secure.setSecure(INCOGNITO_SURFACE_OWNER, target)
        onDispose { }
    }
    DisposableEffect(secure, hasIncognitoTabs) {
        secure.setRecentsHidden(INCOGNITO_TABS_OWNER, hasIncognitoTabs)
        onDispose { }
    }
    DisposableEffect(secure) {
        onDispose {
            secure.setSecure(INCOGNITO_SURFACE_OWNER, false)
            secure.setRecentsHidden(INCOGNITO_TABS_OWNER, false)
        }
    }
}

/**
 * Keeps FLAG_SECURE on for as long as the content of an entry that shows incognito content is
 * composed. NavDisplay keeps a screen it animates away composed until its exit transition is over,
 * and a sheet or dialog until it is removed, so the flag outlasts the last frame of incognito
 * content. An entry leaving the back stack keeps what it last asked for (its tab may already be
 * gone); one that stays follows its tab ([showsIncognito]: a tab that is gone counts as incognito),
 * so the browser lets go as soon as it shows a normal tab.
 *
 * @param keys the back stack now.
 * @param contentKeyOf the content key NavDisplay gives the entry of a key.
 */
@Composable
fun rememberSecureEntryDecorator(
    keys: List<NavKey>,
    contentKeyOf: (NavKey) -> Any,
    tabs: TabsState,
    secure: WindowPrivacy,
): NavEntryDecorator<NavKey> {
    val inBackStack = keys.mapTo(HashSet(), contentKeyOf)
    val incognito = keys.filter { it is SkimKey && showsIncognito(it, tabs) }.mapTo(HashSet(), contentKeyOf)
    val shown = rememberUpdatedState(ShownEntries(inBackStack, incognito))
    return remember(secure) {
        NavEntryDecorator { entry ->
            val hold = remember { SecureHold(secure) }
            val now = shown.value
            if (entry.contentKey in now.inBackStack) {
                val showsIncognito = entry.contentKey in now.incognito
                SideEffect { hold.set(showsIncognito) }
            }
            entry.Content()
        }
    }
}

/** The content keys of the back stack, and of the entries in it that show incognito content. */
private class ShownEntries(val inBackStack: Set<Any>, val incognito: Set<Any>)

/** One entry's hold on FLAG_SECURE, let go when the entry's content leaves the composition. */
private class SecureHold(private val secure: WindowPrivacy) : RememberObserver {
    private val owner = "incognito-entry-${nextHold.incrementAndGet()}"
    private var held = false

    fun set(on: Boolean) {
        if (on == held) return
        held = on
        secure.setSecure(owner, on)
    }

    override fun onRemembered() = Unit

    override fun onForgotten() = set(false)

    override fun onAbandoned() = set(false)

    private companion object {
        val nextHold = AtomicLong()
    }
}
