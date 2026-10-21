package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiFabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    @Test
    fun aDisabledFabHasNoClickActionAndIsADisabledButton() {
        setFab(enabled = false)

        composeRule.onNodeWithContentDescription(LABEL)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }

        assertEquals(0, clicks)
    }

    @Test
    fun anEnabledFabIsAButtonThatOpensSkim() {
        setFab(enabled = true)

        composeRule.onNodeWithContentDescription(LABEL)
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun theDisabledFabIsOnSurfaceAtTenPercent() {
        assertDisabledContainer(dark = false, alpha = 0.10f)
    }

    @Test
    fun theDisabledFabIsOnSurfaceAtTwelvePercentInDark() {
        assertDisabledContainer(dark = true, alpha = 0.12f)
    }

    private fun assertDisabledContainer(dark: Boolean, alpha: Float) {
        var expected = Color.Unspecified
        composeRule.setSkimContent(dark = dark) {
            val colors = MaterialTheme.colorScheme
            expected = colors.onSurface.copy(alpha = alpha).compositeOver(colors.surface)
            Box(Modifier.background(colors.surface).padding(8.dp)) {
                Box(Modifier.size(56.dp).testTag(FAB)) {
                    AiFab(enabled = false, contentDescription = LABEL, onClick = { clicks++ })
                }
            }
        }

        val pixels = composeRule.onNodeWithTag(FAB).captureToImage().toPixelMap()
        // The middle of the left edge: inside the rounded container, clear of the centered icon.
        val sampled = pixels[pixels.width / 10, pixels.height / 2]
        assertTrue(
            "expected $expected, got $sampled",
            listOf(
                expected.red to sampled.red,
                expected.green to sampled.green,
                expected.blue to sampled.blue,
            ).all { (want, got) -> abs(want - got) <= 2f / 255f },
        )
    }

    private fun setFab(enabled: Boolean) {
        composeRule.setSkimContent {
            Box(Modifier.size(56.dp)) {
                AiFab(enabled = enabled, contentDescription = LABEL, onClick = { clicks++ })
            }
        }
    }

    private companion object {
        const val LABEL = "Ask Skim about this page"
        const val FAB = "fab"
    }
}
