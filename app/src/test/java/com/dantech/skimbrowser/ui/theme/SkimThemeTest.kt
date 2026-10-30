package com.dantech.skimbrowser.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.BOARD_STATUS_BAR_HEIGHT
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SkimThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightWithoutDynamicColorUsesTheBrandScheme() {
        val scheme = colorSchemeOf { SkimTheme(ThemeSetting.Light, dynamicColor = false, content = it) }

        assertEquals(Color(0xFF0B57D0), scheme.primary)
        assertEquals(Color(0xFFFFFFFF), scheme.surface)
    }

    @Test
    fun incognitoIsDarkEvenWhenThemeIsLight() {
        val scheme = colorSchemeOf {
            SkimTheme(ThemeSetting.Light, incognito = true, dynamicColor = false, content = it)
        }

        assertEquals(Color(0xFF202124), scheme.surface)
    }

    @Test
    fun dynamicColorUsesTheWallpaperSchemeFromApi31() {
        val scheme = colorSchemeOf { SkimTheme(ThemeSetting.Light, dynamicColor = true, content = it) }

        val wallpaper = dynamicLightColorScheme(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(wallpaper.primary, scheme.primary)
        assertNotEquals(SkimLightColors.primary, scheme.primary)
    }

    @Test
    @Config(sdk = [26])
    fun dynamicColorFallsBackToTheBrandSchemeBeforeApi31() {
        val scheme = colorSchemeOf { SkimTheme(ThemeSetting.Light, content = it) }

        assertEquals(SkimLightColors.primary, scheme.primary)
    }

    @Test
    fun isDarkFollowsTheThemeSettingAndIncognitoIsAlwaysDark() {
        assertTrue(ThemeSetting.System.isDark(systemDark = true, incognito = false))
        assertFalse(ThemeSetting.System.isDark(systemDark = false, incognito = false))
        assertFalse(ThemeSetting.Light.isDark(systemDark = true, incognito = false))
        assertTrue(ThemeSetting.Dark.isDark(systemDark = false, incognito = false))
        ThemeSetting.entries.forEach { setting ->
            assertTrue(setting.isDark(systemDark = false, incognito = true))
        }
    }

    @Test
    @Config(qualifiers = PHONE_QUALIFIERS)
    fun setSkimContentUsesTheBrandSchemeBelowTheBoardStatusBar() {
        lateinit var scheme: ColorScheme
        composeRule.setSkimContent {
            scheme = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.statusBarsPadding().size(1.dp).testTag("content"))
            }
        }

        composeRule.onNodeWithTag("content").assertTopPositionInRootIsEqualTo(BOARD_STATUS_BAR_HEIGHT)
        assertEquals(SkimLightColors.primary, scheme.primary)
    }

    private fun colorSchemeOf(theme: @Composable (content: @Composable () -> Unit) -> Unit): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent { theme { scheme = MaterialTheme.colorScheme } }
        composeRule.waitForIdle()
        return scheme
    }
}
