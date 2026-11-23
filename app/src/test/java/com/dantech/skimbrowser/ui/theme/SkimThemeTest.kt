package com.dantech.skimbrowser.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

        assertEquals(SkimDarkColors.surface, scheme.surface)
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
    fun pageDarkeningFollowsTheThemeAndNotTheIncognitoChrome() {
        assertTrue(ThemeSetting.Dark.isDark(systemDark = false, incognito = false))
        assertFalse(ThemeSetting.Light.isDark(systemDark = true, incognito = false))
        assertTrue(ThemeSetting.System.isDark(systemDark = true, incognito = false))
        assertFalse(ThemeSetting.System.isDark(systemDark = false, incognito = false))
        assertTrue(ThemeSetting.Light.isDark(systemDark = false, incognito = true))
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
    fun chromeThemeIsTheNeutralLightSchemeInsideTheLightTheme() {
        assertNeutral(SkimChromeLightColors, chromeSchemeInside(ThemeSetting.Light))
    }

    @Test
    fun chromeThemeIsTheNeutralDarkSchemeInsideTheDarkTheme() {
        assertNeutral(SkimChromeDarkColors, chromeSchemeInside(ThemeSetting.Dark))
    }

    @Test
    fun chromeThemeIsTheNeutralDarkSchemeInIncognito() {
        assertNeutral(SkimChromeDarkColors, chromeSchemeInside(ThemeSetting.Light, incognito = true))
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun chromeThemeKeepsTheTypeShapesAndMotionAroundIt() {
        lateinit var outside: List<Any>
        lateinit var inside: List<Any>
        composeRule.setContent {
            SkimTheme(ThemeSetting.Light, dynamicColor = false) {
                outside = listOf(MaterialTheme.typography, MaterialTheme.shapes, MaterialTheme.motionScheme)
                SkimChromeTheme {
                    inside = listOf(MaterialTheme.typography, MaterialTheme.shapes, MaterialTheme.motionScheme)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(outside, inside)
    }

    @Test
    fun skimAndChromeSchemesShareTheAccentTheAiRolesAndTheErrorRoles() {
        listOf(SkimLightColors to SkimChromeLightColors, SkimDarkColors to SkimChromeDarkColors).forEach { (skim, chrome) ->
            assertEquals(chrome.primary, skim.primary)
            assertEquals(chrome.onPrimary, skim.onPrimary)
            assertEquals(chrome.primaryContainer, skim.primaryContainer)
            assertEquals(chrome.tertiary, skim.tertiary)
            assertEquals(chrome.tertiaryContainer, skim.tertiaryContainer)
            assertEquals(chrome.onTertiaryContainer, skim.onTertiaryContainer)
            assertEquals(chrome.error, skim.error)
            assertEquals(chrome.errorContainer, skim.errorContainer)
        }
    }

    @Test
    fun aTintedRoleIsAsLightAsItsNeutralRoleSoTextContrastDoesNotMove() {
        listOf(SkimLightColors to SkimChromeLightColors, SkimDarkColors to SkimChromeDarkColors).forEach { (skim, chrome) ->
            val dark = skim === SkimDarkColors
            val roles = listOfNotNull(
                "surface" to (skim.surface to chrome.surface),
                "surfaceContainerLow" to (skim.surfaceContainerLow to chrome.surfaceContainerLow),
                "surfaceContainer" to (skim.surfaceContainer to chrome.surfaceContainer),
                // The dark scheme gives surfaceContainerHigh a step of its own, checked below.
                ("surfaceContainerHigh" to (skim.surfaceContainerHigh to chrome.surfaceContainerHigh)).takeUnless { dark },
                "surfaceContainerHighest" to (skim.surfaceContainerHighest to chrome.surfaceContainerHighest),
                "secondaryContainer" to (skim.secondaryContainer to chrome.secondaryContainer),
                "onSecondaryContainer" to (skim.onSecondaryContainer to chrome.onSecondaryContainer),
                "onSurface" to (skim.onSurface to chrome.onSurface),
                "onSurfaceVariant" to (skim.onSurfaceVariant to chrome.onSurfaceVariant),
                "outline" to (skim.outline to chrome.outline),
                "outlineVariant" to (skim.outlineVariant to chrome.outlineVariant),
                "inverseSurface" to (skim.inverseSurface to chrome.inverseSurface),
                "inverseOnSurface" to (skim.inverseOnSurface to chrome.inverseOnSurface),
            )
            roles.forEach { (role, pair) ->
                val (tinted, neutral) = pair
                // The contrast of one against the other: 1 is the same luminance.
                val lighter = maxOf(tinted.luminance(), neutral.luminance())
                val darker = minOf(tinted.luminance(), neutral.luminance())
                val contrast = (lighter + 0.05f) / (darker + 0.05f)
                assertTrue("$role in the ${if (dark) "dark" else "light"} scheme: $contrast", contrast < 1.03f)
            }
        }
    }

    @Test
    fun darkSurfaceContainerHighIsAStepBetweenTheContainerAndTheHighest() {
        val high = SkimDarkColors.surfaceContainerHigh.luminance()

        assertTrue(SkimDarkColors.surfaceContainer.luminance() < high)
        assertTrue(high < SkimDarkColors.surfaceContainerHighest.luminance())
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

    @Test
    fun setSkimContentGivesContentTheOnSurfaceColorOfTheAppWindow() {
        assertEquals(SkimLightColors.onSurface, contentColorInSkimContent())
    }

    @Test
    fun setSkimContentGivesDarkContentTheDarkOnSurfaceColor() {
        assertEquals(SkimDarkColors.onSurface, contentColorInSkimContent(dark = true))
    }

    @Test
    fun setSkimContentGivesIncognitoContentTheDarkOnSurfaceColor() {
        assertEquals(SkimDarkColors.onSurface, contentColorInSkimContent(incognito = true))
    }

    /** The color of text and icons that set none of their own, inside the screenshot fixture. */
    private fun contentColorInSkimContent(dark: Boolean = false, incognito: Boolean = false): Color {
        var color = Color.Unspecified
        composeRule.setSkimContent(dark = dark, incognito = incognito) { color = LocalContentColor.current }
        composeRule.waitForIdle()
        return color
    }

    /** The scheme in use inside [SkimChromeTheme], under the Skim theme for [theme]. */
    private fun chromeSchemeInside(theme: ThemeSetting, incognito: Boolean = false): ColorScheme =
        colorSchemeOf { content ->
            SkimTheme(theme, incognito = incognito, dynamicColor = false) { SkimChromeTheme(content) }
        }

    private fun assertNeutral(expected: ColorScheme, actual: ColorScheme) {
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.surfaceContainer, actual.surfaceContainer)
        assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.onSurfaceVariant, actual.onSurfaceVariant)
        assertEquals(expected.outline, actual.outline)
    }

    private fun colorSchemeOf(theme: @Composable (content: @Composable () -> Unit) -> Unit): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent { theme { scheme = MaterialTheme.colorScheme } }
        composeRule.waitForIdle()
        return scheme
    }
}
