package com.dantech.skimbrowser.system

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowRoleManager

/**
 * Whether Skim is the default browser and what Set as default opens, on the system's package
 * manager and role manager. Skim's own manifest makes it a browser: its activity takes web links.
 */
@RunWith(AndroidJUnit4::class)
class DefaultBrowserTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val packages get() = shadowOf(context.packageManager)
    private val skim = ComponentName(context, MainActivity::class.java)
    private val chrome = ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main")
    private val settingsApp = ComponentName("com.android.settings", "com.android.settings.DefaultAppsSettings")

    // Android 8 and 9: the app web links open in

    @Config(sdk = [26])
    @Test
    fun beforeRolesSkimAloneTakingWebLinksIsTheDefault() {
        assertTrue(DefaultBrowser.isDefault(context))
    }

    @Config(sdk = [26])
    @Test
    fun beforeRolesAnotherBrowserTakingWebLinksIsTheDefault() {
        packages.clearIntentFilterForActivity(skim)
        install(chrome, webLinks())

        assertFalse(DefaultBrowser.isDefault(context))
    }

    @Config(sdk = [26])
    @Test
    fun beforeRolesTwoBrowsersWithoutAChoiceMeanNoDefault() {
        install(chrome, webLinks())
        // With two apps for web links and none chosen, the system answers with its chooser.
        packages.setShouldShowActivityChooser(true)

        assertFalse(DefaultBrowser.isDefault(context))
    }

    @Config(sdk = [26])
    @Test
    fun beforeRolesSetAsDefaultOpensTheDefaultAppsSettings() {
        install(settingsApp, IntentFilter(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })

        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, DefaultBrowser.requestIntent(context).action)
    }

    @Config(sdk = [26])
    @Test
    fun withoutTheDefaultAppsScreenSetAsDefaultOpensTheSettings() {
        assertEquals(Settings.ACTION_SETTINGS, DefaultBrowser.requestIntent(context).action)
    }

    // Android 10 and later: the browser role

    @Test
    fun withRolesSkimIsTheDefaultOnlyWhileItHoldsTheBrowserRole() {
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_BROWSER, chrome.packageName, Process.myUserHandle())
        assertFalse("web links alone do not make the default", DefaultBrowser.isDefault(context))

        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_BROWSER, context.packageName, Process.myUserHandle())

        assertTrue(DefaultBrowser.isDefault(context))
    }

    @Test
    fun withRolesSetAsDefaultAsksForTheBrowserRole() {
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_BROWSER, chrome.packageName, Process.myUserHandle())
        install(settingsApp, IntentFilter(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })

        val request = DefaultBrowser.requestIntent(context)

        assertEquals(REQUEST_ROLE, request.action)
        assertEquals(RoleManager.ROLE_BROWSER, request.getStringExtra(EXTRA_ROLE_NAME))
    }

    @Test
    fun onceSkimHoldsTheRoleSetAsDefaultOpensTheDefaultAppsSettings() {
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_BROWSER, context.packageName, Process.myUserHandle())
        install(settingsApp, IntentFilter(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })

        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, DefaultBrowser.requestIntent(context).action)
    }

    @Test
    fun withoutTheBrowserRoleSkimIsNotTheDefaultAndSetAsDefaultOpensTheSettings() {
        assertFalse(DefaultBrowser.isDefault(context))
        assertEquals(Settings.ACTION_SETTINGS, DefaultBrowser.requestIntent(context).action)
    }

    private fun install(activity: ComponentName, filter: IntentFilter) {
        packages.addActivityIfNotPresent(activity)
        packages.addIntentFilterForActivity(activity, filter)
    }

    private fun webLinks() = IntentFilter(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
        addDataScheme("http")
        addDataScheme("https")
    }

    private companion object {
        // RoleManager.createRequestRoleIntent's action and extra; the SDK does not publish them.
        const val REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE"
        const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    }
}
