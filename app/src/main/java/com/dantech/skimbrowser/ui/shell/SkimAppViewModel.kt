package com.dantech.skimbrowser.ui.shell

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.dantech.skimbrowser.domain.browsing.WindowPrivacy
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.tab.TabRestore
import com.dantech.skimbrowser.domain.tab.TabsController
import com.dantech.skimbrowser.domain.tab.TabsState
import com.dantech.skimbrowser.domain.tab.newTab
import com.dantech.skimbrowser.ui.navigation.BrowserKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.navigation.WelcomeKey
import com.dantech.skimbrowser.ui.navigation.reconcileBackStack
import com.dantech.skimbrowser.ui.navigation.skimNavConfig
import com.dantech.skimbrowser.ui.snackbar.SnackbarController
import com.dantech.skimbrowser.ui.snackbar.UndoRegistry
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.PolymorphicSerializer

/** What the main window shows: the empty window in [theme] until the app has [started], then its back stack. */
data class SkimAppUiState(val theme: ThemeSetting = ThemeSetting.System, val started: Boolean = false)

/**
 * The app of the main window, for [SkimApp]. It is kept while the Activity is recreated, and its
 * back stack is saved with the Activity, so it also comes back after the process went away.
 *
 * It starts once the settings are read and the saved tabs restored (or given up on: a restore that
 * fails or takes too long still opens the browser, on a New tab). The first launch opens on Welcome
 * (01), later ones on the browser with the last tab; a back stack saved before the process went
 * away wins over both, once the keys of tabs that did not come back are dropped.
 *
 * Once it has started, [navigator] is the only writer of [backStack]. The navigator holds no window:
 * the Activity that shows the app lends its own with [attachWindow] while its content is composed.
 */
@HiltViewModel
class SkimAppViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settings: SettingsRepository,
    private val tabsController: TabsController,
    private val tabRestore: TabRestore,
    /** The window's one snackbar. */
    val snackbar: SnackbarController,
    /** Runs the Undo of a snackbar action. */
    val undo: UndoRegistry,
    navigatorFactory: SkimNavigator.Factory,
) : ViewModel() {

    private val savedBackStack: NavBackStack<NavKey> by savedState.saved(
        serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
        key = BACK_STACK_KEY,
        configuration = skimNavConfig,
    ) { NavBackStack() }

    /** The destinations of the window, bottom to top. Empty until the app has started. */
    val backStack: List<NavKey> get() = savedBackStack

    private val window = AttachedWindow()

    /** Every move between the destinations of [backStack]. */
    val navigator: SkimNavigator = navigatorFactory.create(savedBackStack, window, viewModelScope)

    /**
     * The open tabs. It is the controller's own flow, not a copy, so the shell sees a change in the
     * same frame as the screens do and decides FLAG_SECURE from what they draw.
     */
    val tabs: StateFlow<TabsState> = tabsController.state

    private val started = MutableStateFlow(false)

    val state: StateFlow<SkimAppUiState> = combine(
        settings.settings.map { it.theme }.onStart { emit(ThemeSetting.System) },
        started,
        ::SkimAppUiState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, SkimAppUiState())

    init {
        viewModelScope.launch {
            val onboarded = startUp()
            if (savedBackStack.isEmpty()) {
                savedBackStack.add(if (onboarded) BrowserKey else WelcomeKey)
            } else {
                // A restored back stack may point at tabs that did not come back; it is fixed once, before it shows.
                savedBackStack.reconcileWith(tabs.value)
            }
            started.value = true
        }
    }

    /**
     * Lends the navigator [window], the window of the Activity whose content shows the app now,
     * until [detachWindow].
     */
    @MainThread
    fun attachWindow(window: WindowPrivacy) = this.window.attach(window)

    @MainThread
    fun detachWindow(window: WindowPrivacy) = this.window.detach(window)

    /** Opens [url], a link another app sent, in a new normal tab and shows it at once. */
    @MainThread
    fun openExternalLink(url: String) {
        tabsController.newTab(incognito = false, url = url, openedByExternalApp = true)
        navigator.showBrowser()
    }

    /**
     * Reads whether onboarding is done and waits for the saved tabs. Neither can hold the start up
     * for long: settings that cannot be read count as a first launch, and tabs that are not restored
     * in time (or not at all) leave the browser to open a New tab.
     */
    private suspend fun startUp(): Boolean {
        val onboarded = withTimeoutOrNull(START_TIMEOUT_MS) { settings.settings.first().onboardingCompleted }
        if (onboarded == null) Log.w(TAG, "Settings were not read in time")
        try {
            if (withTimeoutOrNull(START_TIMEOUT_MS) { tabRestore.restore() } == null) {
                Log.w(TAG, "Saved tabs were not restored in time")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saved tabs could not be restored: ${e.javaClass.simpleName}")
        }
        return onboarded == true
    }

    internal companion object {
        private const val TAG = "SkimApp"

        /** Where the back stack is saved in the view model's saved state. */
        const val BACK_STACK_KEY = "backStack"

        /** Longer than the tab manager's own restore timeout, which normally ends the wait first. */
        const val START_TIMEOUT_MS = 8_000L
    }
}

/** Drops the keys of tabs that are not open, in place. */
private fun NavBackStack<NavKey>.reconcileWith(tabs: TabsState) {
    val keys = toList()
    val reconciled = reconcileBackStack(keys, tabs)
    if (reconciled == keys) return
    clear()
    addAll(reconciled)
}

/**
 * The window of the Activity that shows the app, while one is attached. The view model outlives
 * that Activity, so the navigator turns FLAG_SECURE on through this rather than holding a window.
 * While none is attached nothing is on screen, and the next window settles the flag from what it
 * shows ([SecureFlagEffect]) before its first frame is drawn.
 */
@MainThread
internal class AttachedWindow : WindowPrivacy {
    private var window: WindowPrivacy? = null

    fun attach(window: WindowPrivacy) {
        this.window = window
    }

    /** Lets [window] go, unless another window was attached since. */
    fun detach(window: WindowPrivacy) {
        if (this.window === window) this.window = null
    }

    override fun setSecure(owner: String, secure: Boolean) {
        window?.setSecure(owner, secure)
    }

    override fun setRecentsHidden(owner: String, hidden: Boolean) {
        window?.setRecentsHidden(owner, hidden)
    }
}
