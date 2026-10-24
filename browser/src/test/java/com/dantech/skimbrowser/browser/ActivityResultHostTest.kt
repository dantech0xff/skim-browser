package com.dantech.skimbrowser.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ActivityResultHostTest {

    /** A registry that records launches and lets the test deliver their results. */
    private class TestRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Pair<Int, Any?>>()
        var launchFailure: RuntimeException? = null

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launchFailure?.let { throw it }
            launches += requestCode to input
        }
    }

    private val registry = TestRegistry()
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
    private val activity = controller.get()
    private val host = ActivityResultHost(activity, registry)

    init {
        controller.start().resume()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun aPickerResultReachesItsRequestOnce() {
        val results = mutableListOf<Pair<Int, Intent?>>()
        val picker = Intent(Intent.ACTION_GET_CONTENT)

        host.chooseFiles(picker) { code, data -> results += code to data }

        val (requestCode, input) = registry.launches.single()
        assertSame(picker, input)
        val data = Intent()
        registry.dispatchResult(requestCode, Activity.RESULT_OK, data)
        registry.dispatchResult(requestCode, Activity.RESULT_OK, data)
        assertEquals(listOf(Activity.RESULT_OK to data), results)
    }

    @Test
    fun aNewPickerRequestCancelsTheOneWaiting() {
        val first = mutableListOf<Pair<Int, Intent?>>()
        val second = mutableListOf<Pair<Int, Intent?>>()
        host.chooseFiles(Intent(Intent.ACTION_GET_CONTENT)) { code, data -> first += code to data }

        host.chooseFiles(Intent(Intent.ACTION_GET_CONTENT)) { code, data -> second += code to data }

        assertEquals(listOf<Pair<Int, Intent?>>(Activity.RESULT_CANCELED to null), first)
        registry.dispatchResult(registry.launches.last().first, Activity.RESULT_OK, null)
        assertEquals(listOf<Pair<Int, Intent?>>(Activity.RESULT_OK to null), second)
        assertEquals(1, first.size)
    }

    @Test
    fun withoutAPickerTheRequestIsCancelledAtOnce() {
        registry.launchFailure = ActivityNotFoundException()
        val results = mutableListOf<Pair<Int, Intent?>>()

        host.chooseFiles(Intent(Intent.ACTION_GET_CONTENT)) { code, data -> results += code to data }

        assertEquals(listOf<Pair<Int, Intent?>>(Activity.RESULT_CANCELED to null), results)
    }

    @Test
    fun destroyingTheActivityCancelsWhatWaitsAndWhatComesLater() {
        val files = mutableListOf<Pair<Int, Intent?>>()
        val permissions = mutableListOf<Map<String, Boolean>>()
        host.chooseFiles(Intent(Intent.ACTION_GET_CONTENT)) { code, data -> files += code to data }
        host.requestPermissions(arrayOf(CAMERA)) { permissions += it }

        host.destroy()
        assertEquals("what waited is answered at once", listOf<Pair<Int, Intent?>>(Activity.RESULT_CANCELED to null), files)
        assertEquals(listOf(mapOf(CAMERA to false)), permissions)
        host.chooseFiles(Intent(Intent.ACTION_GET_CONTENT)) { code, data -> files += code to data }
        host.requestPermissions(arrayOf(CAMERA)) { permissions += it }

        assertEquals(listOf<Pair<Int, Intent?>>(Activity.RESULT_CANCELED to null, Activity.RESULT_CANCELED to null), files)
        assertEquals(listOf(mapOf(CAMERA to false), mapOf(CAMERA to false)), permissions)
        assertEquals("nothing launched after the Activity went", 2, registry.launches.size)
    }

    @Test
    fun permissionAnswersCoverEveryPermissionAsked() {
        val answers = mutableListOf<Map<String, Boolean>>()

        host.requestPermissions(arrayOf(CAMERA, MICROPHONE)) { answers += it }
        registry.dispatchResult(registry.launches.single().first, mapOf(CAMERA to true))

        assertEquals(listOf(mapOf(CAMERA to true, MICROPHONE to false)), answers)
    }

    @Test
    fun aNewPermissionRequestDeniesTheOneWaiting() {
        val first = mutableListOf<Map<String, Boolean>>()
        val second = mutableListOf<Map<String, Boolean>>()
        host.requestPermissions(arrayOf(CAMERA)) { first += it }

        host.requestPermissions(arrayOf(MICROPHONE)) { second += it }
        registry.dispatchResult(registry.launches.last().first, mapOf(MICROPHONE to true))

        assertEquals(listOf(mapOf(CAMERA to false)), first)
        assertEquals(listOf(mapOf(MICROPHONE to true)), second)
    }

    @Test
    fun askingForNothingIsAnsweredAtOnce() {
        val answers = mutableListOf<Map<String, Boolean>>()

        host.requestPermissions(emptyArray()) { answers += it }

        assertEquals(listOf(emptyMap<String, Boolean>()), answers)
        assertTrue(registry.launches.isEmpty())
    }

    @Test
    fun anotherAppStartsFromTheActivity() {
        val intent = Intent(Intent.ACTION_VIEW).setPackage("com.example.maps")

        host.startExternal(intent)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("com.example.maps", started.`package`)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private companion object {
        const val CAMERA = "android.permission.CAMERA"
        const val MICROPHONE = "android.permission.RECORD_AUDIO"
    }
}
