package com.dantech.skimbrowser.ui.shell

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.di.ProcessGraph
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.MainActivity
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.di.AppGraphEntryPoint
import com.dantech.skimbrowser.system.purgeOldShares
import dagger.hilt.android.EntryPointAccessors
import com.dantech.skimbrowser.browser.DEVICE_TIMEOUT_MS
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.SkimWebView
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TestPages
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.viewsIn
import com.dantech.skimbrowser.browser.waitUntil
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource

/**
 * The app as a user runs it, for the device tests of its shell: MainActivity over the process's own
 * container, so its tabs, settings, incognito session and snackbar are the app's. A test sets up
 * what it needs first (whether onboarding is done, the tabs it opens), then [launch]es the
 * Activity the way a user or another app would, and drives it through the app's navigator.
 *
 * Before the test the saved tabs are read, as the app does when it starts, every tab left open is
 * closed for good and the settings are noted. Afterwards everything the test changed is undone:
 * every MainActivity is finished, every tab closed for good, the incognito session ended, the
 * snackbar dismissed, the settings put back as they were, the history, cookies and site storage
 * cleared, and the page server stopped.
 */
class SkimAppRule : ExternalResource() {

    lateinit var app: Application
        private set
    lateinit var graph: ProcessGraph
        private set
    val tabs: TabManager get() = graph.tabManager()
    val pages = TestPages()

    private val scenarios = ArrayList<ActivityScenario<MainActivity>>()
    private val instances = MainActivities()
    private lateinit var settingsBefore: Preferences

    override fun before() {
        app = ApplicationProvider.getApplicationContext()
        TestStorage.current.set(AppStorage())
        graph = EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).graph()
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                tabs.restore()
                purgeOldShares(app.cacheDir)
                settingsBefore = graph.dataStore().data.first()
            }
        }
        closeEveryTab()
        pages.start()
        app.registerActivityLifecycleCallbacks(instances)
    }

    override fun after() {
        val failures = ArrayList<Throwable>()
        fun step(block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                failures += e
            }
        }
        step(::finishEveryActivity)
        step(::closeEveryTab)
        step {
            onMain {
                graph.snackbarController().current.value?.let { graph.snackbarController().onDismissed(it.id) }
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebStorage.getInstance().deleteAllData()
            }
        }
        step {
            runBlocking {
                withTimeout(DEVICE_TIMEOUT_MS) {
                    graph.historyRepository().clearAll()
                    graph.dataStore().updateData { settingsBefore }
                }
            }
        }
        step {
            if (::app.isInitialized) {
                EntryPointAccessors.fromApplication(app, AppGraphEntryPoint::class.java).shutdown().close()
                TestStorage.current.set(null)
            }
        }
        if (::app.isInitialized) app.unregisterActivityLifecycleCallbacks(instances)
        pages.close()
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /**
     * Onboarding as the next start finds it: done, or never done (the settings of a fresh install,
     * where the first launch shows Welcome).
     */
    fun setOnboarded(done: Boolean) {
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                if (done) graph.settings().setOnboardingCompleted() else graph.dataStore().updateData { emptyPreferences() }
            }
        }
    }

    /** Opens a tab on [url] (the New tab page when null) and returns its id; it becomes current unless [background]. */
    fun openTab(url: String?, incognito: Boolean = false, background: Boolean = false): String =
        onMain { tabs.newTab(incognito = incognito, url = url, background = background) }

    /** The open tabs showing [url]. */
    fun tabsShowing(url: String): List<Tab> = tabs.state.value.tabs.filter { it.url == url }

    /** Starts MainActivity with [intent], the launcher's by default; it is finished after the test. */
    fun launch(intent: Intent = launcherIntent()): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(intent).also { scenarios += it }

    /** What the launcher sends when Skim's icon is tapped. */
    fun launcherIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(app, MainActivity::class.java)

    /**
     * What another app sends to open [uri] in Skim. It names Skim's exported activity, as any app
     * may: no intent filter stands between it and the activity, so it can carry any scheme.
     */
    fun linkIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, uri.toUri()).setClass(app, MainActivity::class.java)

    /**
     * Sends [intent] to Skim while it runs, as another app does: the single-task activity gets it
     * through onNewIntent.
     */
    fun send(intent: Intent) {
        app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The scenario's activity now (a recreated one after `recreate`). */
    fun activityOf(scenario: ActivityScenario<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        return checkNotNull(activity)
    }

    /** The navigator of the scenario's activity, once the app has read its settings and tabs. */
    fun navigatorOf(scenario: ActivityScenario<MainActivity>): SkimNavigator {
        var navigator: SkimNavigator? = null
        waitUntil("the app to start") {
            scenario.onActivity { navigator = it.navigatorForTests }
            navigator != null
        }
        return checkNotNull(navigator)
    }

    /** The back stack of the scenario's activity, bottom to top. */
    fun keysOf(scenario: ActivityScenario<MainActivity>): List<NavKey> {
        val navigator = navigatorOf(scenario)
        return onMain { navigator.keysForTests }
    }

    /** The system Back button, through the input pipeline to the focused window. */
    fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** Finishes every MainActivity, the scenarios' and those that intents of the test started. */
    private fun finishEveryActivity() {
        val failures = ArrayList<Throwable>()
        scenarios.forEach { scenario ->
            try {
                scenario.close()
            } catch (e: Throwable) {
                failures += e
            }
        }
        scenarios.clear()
        onMain { instances.alive.toList() }.forEach { activity -> onMain { activity.finish() } }
        waitUntil("every MainActivity to be destroyed") { onMain { instances.alive.isEmpty() } }
        failures.firstOrNull()?.let { throw it }
    }

    /** Closes every tab for good and ends the incognito session. */
    private fun closeEveryTab() {
        onMain {
            val open = tabs.state.value.tabs.map { it.id }
            if (open.isNotEmpty()) tabs.finalizeClose(tabs.close(open))
            tabs.finalizePendingCloses()
            if (graph.incognitoSession().state.value != IncognitoSession.State.None) graph.incognitoSession().end(engine = null)
        }
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                tabs.awaitWrites()
                graph.tabStateStore().awaitIdle()
                graph.thumbnails().awaitIdle()
            }
        }
    }

    /** The MainActivity instances alive in the process; read on the main thread. */
    private class MainActivities : Application.ActivityLifecycleCallbacks {
        val alive = ArrayList<MainActivity>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is MainActivity) alive += activity
        }

        override fun onActivityDestroyed(activity: Activity) {
            alive.remove(activity)
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}

/** True while FLAG_SECURE is set on the activity's window; call on the main thread. */
internal fun Activity.isSecure(): Boolean = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

/** The tabs' WebViews the activity's window shows on screen now (not those parked off screen); call on the main thread. */
internal fun Activity.webViewsOnScreen(): List<SkimWebView> =
    viewsIn<SkimWebView>(window.decorView).filter { it.isOnScreen() }

/** Whether any part of the view is on screen: shown, and not moved outside the window. */
internal fun View.isOnScreen(): Boolean = isShown && getGlobalVisibleRect(Rect())
