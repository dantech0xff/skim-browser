package com.dantech.skimbrowser.ui.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.fake.FakeSitePermissionRepository
import com.dantech.skimbrowser.domain.permission.SkimAiState
import com.dantech.skimbrowser.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Allow writes the site's choice before the sheet replaces the dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiPermissionViewModelTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun allowRecordsTheSiteThenContinues() = runTest(main.testDispatcher) {
        val sites = FakeSitePermissionRepository()
        val model = AiPermissionViewModel(sites)
        var continued = false

        model.allow("daylightjournal.com") { continued = true }

        assertFalse(continued)
        advanceUntilIdle()
        assertEquals(SkimAiState.ALLOWED, sites.get("daylightjournal.com")?.skimAi)
        assertEquals(true, continued)
    }
}
