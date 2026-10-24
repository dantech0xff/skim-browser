package com.dantech.skimbrowser.ui.shell

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.MainActivity
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.di.DeviceAccess
import com.dantech.skimbrowser.system.purgeOldShares
import dagger.hilt.android.EntryPointAccessors
import com.dantech.skimbrowser.browser.DEVICE_TIMEOUT_MS
import com.dantech.skimbrowser.browser.IncognitoSession
import com.dantech.skimbrowser.browser.Tab
import com.dantech.skimbrowser.browser.TabManager
import com.dantech.skimbrowser.browser.TestPages
import com.dantech.skimbrowser.browser.onMain
import com.dantech.skimbrowser.browser.waitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource

/**
 * The app as a user runs it, for the device tests of its shell: MainActivity over the process's own
 * container, so its tabs, settings, incognito session and snackbar are the app's. A test sets up
 * what it needs first (whether onboarding is done), then [launch]es the Activity the way a user or
 * another app would.
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
    lateinit var access: DeviceAccess
        private set
    val tabs: TabManager get() = access.tabManager()
    val pages = TestPages()

    private val scenarios = ArrayList<ActivityScenario<MainActivity>>()
    private val instances = MainActivities()
    private lateinit var settingsBefore: Preferences

    override fun before() {
        app = ApplicationProvider.getApplicationContext()
        TestStorage.current.set(AppStorage())
        access = EntryPointAccessors.fromApplication(app, DeviceAccess::class.java)
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                tabs.restore()
                purgeOldShares(app.cacheDir)
                settingsBefore = access.dataStore().data.first()
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
                access.snackbarController().current.value?.let { access.snackbarController().onDismissed(it.id) }
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
                    access.historyRepository().clearAll()
                    access.dataStore().updateData { settingsBefore }
                }
            }
        }
        step {
            if (::access.isInitialized) access.release().release()
            TestStorage.current.set(null)
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
                if (done) access.settings().setOnboardingCompleted() else access.dataStore().updateData { emptyPreferences() }
            }
        }
    }

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
            if (access.incognitoSession().state.value != IncognitoSession.State.None) access.incognitoSession().end(engine = null)
        }
        runBlocking {
            withTimeout(DEVICE_TIMEOUT_MS) {
                tabs.awaitWrites()
                access.tabStateStore().awaitIdle()
                access.thumbnails().awaitIdle()
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
