package com.dantech.skimbrowser.testing

import android.os.Handler
import android.os.Looper
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The paused main looper must not block inside Espresso while Compose is settling. */
@RunWith(AndroidJUnit4::class)
class PausedLooperIdlingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theComposeLinkIsNotAnEspressoResource() {
        val link = busy(ShadowSkimEspresso.COMPOSE_LINK)

        assertFalse(Espresso.registerIdlingResources(link))
        assertTrue(Espresso.getIdlingResources().none { it.name == ShadowSkimEspresso.COMPOSE_LINK })
    }

    @Test
    fun anotherIdlingResourceStillRegisters() {
        val resource = busy("skim-test-resource")

        assertTrue(Espresso.registerIdlingResources(resource))
        assertTrue(Espresso.getIdlingResources().any { it.name == "skim-test-resource" })

        Espresso.unregisterIdlingResources(resource)
    }

    @Test
    fun onIdleReturnsWhileAFrameIsStillInTheFuture() {
        var clicks = 0
        composeRule.setContent {
            Button(onClick = { clicks += 1 }) { Text("Go") }
        }
        Handler(Looper.getMainLooper()).postDelayed({}, 10_000)

        val started = System.nanoTime()
        composeRule.onNodeWithText("Go").performClick()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(1, clicks)
        assertTrue("performClick blocked for ${elapsedMs}ms", elapsedMs < 5_000)
    }

    private fun busy(name: String) = object : IdlingResource {
        override fun getName() = name
        override fun isIdleNow() = false
        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) = Unit
    }
}
