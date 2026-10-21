package com.dantech.skimbrowser.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class A11yAssertionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelledControlsPassEvenWhenDrawnSmallerThan48() {
        composeRule.setSkimContent {
            Row {
                IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_arrow_back), "Back") }
                // 36 dp, like the snackbar action: its touch bounds still reach 48.
                Box(Modifier.size(36.dp).clickable {}) { Text("Undo") }
            }
        }

        composeRule.assertA11y()
    }

    @Test
    fun aClickableWithoutALabelFails() {
        composeRule.setSkimContent {
            Box(Modifier.size(48.dp).clickable {})
        }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertA11y() }
        assertTrue(error.message, error.message!!.contains("no text or content description"))
    }

    @Test
    fun aToggleUnder48WithoutAClickActionFails() {
        composeRule.setSkimContent {
            Box(
                Modifier.size(32.dp).semantics {
                    contentDescription = "Wi-Fi"
                    toggleableState = ToggleableState.On
                },
            )
        }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertA11y() }
        assertTrue(error.message, error.message!!.contains("\"Wi-Fi\" has a touch area of 32.0.dp by 32.0.dp"))
    }
}
