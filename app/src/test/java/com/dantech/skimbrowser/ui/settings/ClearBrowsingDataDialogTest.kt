package com.dantech.skimbrowser.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Clear stays off until a box is checked, and reports the boxes that are on. */
@RunWith(AndroidJUnit4::class)
class ClearBrowsingDataDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearIsOffWhenNothingIsCheckedAndReportsTheSelection() {
        var cleared: Triple<Boolean, Boolean, Boolean>? = null
        composeRule.setSkimContent {
            ClearBrowsingDataDialog(
                onClear = { history, cookies, cache -> cleared = Triple(history, cookies, cache) },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Clear").assertIsEnabled()
        composeRule.onNodeWithText("Browsing history").performClick()
        composeRule.onNodeWithText("Cookies and site data").performClick()
        composeRule.onNodeWithText("Clear").assertIsNotEnabled()

        composeRule.onNodeWithText("Cached images and files").performClick()
        composeRule.onNodeWithText("Clear").assertIsEnabled().performClick()
        assertEquals(Triple(false, false, true), cleared)
    }
}
