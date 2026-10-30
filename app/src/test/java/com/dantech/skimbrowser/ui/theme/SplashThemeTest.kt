package com.dantech.skimbrowser.ui.theme

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.splashscreen.R as SplashR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The splash theme the system reads before [com.dantech.skimbrowser.MainActivity] swaps in
 * [R.style.Theme_SkimBrowser]. Day and night are separate styles, so both are resolved here.
 */
@RunWith(AndroidJUnit4::class)
class SplashThemeTest {

    @Test
    fun daySplashUsesTheLightWindowAndHandsBackTheAppTheme() {
        val theme = splashTheme()

        assertEquals(Color.parseColor("#FFFFFF"), color(theme, SplashR.attr.windowSplashScreenBackground))
        assertEquals(R.drawable.ic_splash_icon, res(theme, SplashR.attr.windowSplashScreenAnimatedIcon))
        assertEquals(R.style.Theme_SkimBrowser, res(theme, SplashR.attr.postSplashScreenTheme))
    }

    @Test
    @Config(qualifiers = "night")
    fun nightSplashUsesTheDarkWindowAndHandsBackTheAppTheme() {
        val theme = splashTheme()

        assertEquals(Color.parseColor("#202124"), color(theme, SplashR.attr.windowSplashScreenBackground))
        assertEquals(R.drawable.ic_splash_icon, res(theme, SplashR.attr.windowSplashScreenAnimatedIcon))
        assertEquals(R.style.Theme_SkimBrowser, res(theme, SplashR.attr.postSplashScreenTheme))
    }

    @Test
    @Config(sdk = [31])
    fun platformSplashAttrsAtApi31CarryTheDayWindow() {
        val theme = splashTheme()

        assertEquals(Color.parseColor("#FFFFFF"), color(theme, android.R.attr.windowSplashScreenBackground))
        assertEquals(R.drawable.ic_splash_icon, res(theme, android.R.attr.windowSplashScreenAnimatedIcon))
    }

    @Test
    @Config(sdk = [31], qualifiers = "night")
    fun platformSplashAttrsAtApi31CarryTheNightWindow() {
        val theme = splashTheme()

        assertEquals(Color.parseColor("#202124"), color(theme, android.R.attr.windowSplashScreenBackground))
        assertEquals(R.drawable.ic_splash_icon, res(theme, android.R.attr.windowSplashScreenAnimatedIcon))
    }

    @Test
    @Config(sdk = [33])
    fun api33PrefersTheSplashIconOnANonLauncherStart() {
        assertEquals(ICON_PREFERRED, integer(splashTheme(), android.R.attr.windowSplashScreenBehavior))
    }

    @Test
    @Config(sdk = [33], qualifiers = "night")
    fun api33NightPrefersTheSplashIconAndKeepsTheDarkWindow() {
        val theme = splashTheme()

        assertEquals(ICON_PREFERRED, integer(theme, android.R.attr.windowSplashScreenBehavior))
        assertEquals(Color.parseColor("#202124"), color(theme, SplashR.attr.windowSplashScreenBackground))
    }

    @Test
    @Config(sdk = [26])
    fun splashIconInflatesUnderTheSplashThemeAtApi26() {
        assertNotNull(AppCompatResources.getDrawable(splashContext(), R.drawable.ic_splash_icon))
    }

    @Test
    @Config(sdk = [30])
    fun splashIconInflatesUnderTheSplashThemeAtApi30() {
        assertNotNull(AppCompatResources.getDrawable(splashContext(), R.drawable.ic_splash_icon))
    }

    private fun splashContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return ContextThemeWrapper(base, R.style.Theme_SkimBrowser_Splash)
    }

    private fun splashTheme() = splashContext().theme

    private fun color(theme: android.content.res.Resources.Theme, attr: Int): Int {
        val typed = theme.obtainStyledAttributes(intArrayOf(attr))
        return typed.getColor(0, Color.TRANSPARENT).also { typed.recycle() }
    }

    private fun res(theme: android.content.res.Resources.Theme, attr: Int): Int {
        val typed = theme.obtainStyledAttributes(intArrayOf(attr))
        return typed.getResourceId(0, 0).also { typed.recycle() }
    }

    private fun integer(theme: android.content.res.Resources.Theme, attr: Int): Int {
        val typed = theme.obtainStyledAttributes(intArrayOf(attr))
        return typed.getInt(0, -1).also { typed.recycle() }
    }

    private companion object {
        /** Platform enum value of `icon_preferred` on `android:windowSplashScreenBehavior`. */
        const val ICON_PREFERRED = 1
    }
}
