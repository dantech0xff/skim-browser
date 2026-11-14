package com.dantech.skimbrowser.ui.browsing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.ReleaseProcessResources
import com.dantech.skimbrowser.TestStorage
import com.dantech.skimbrowser.AppStorage
import com.dantech.skimbrowser.domain.video.VideoCommands
import com.dantech.skimbrowser.domain.video.VideoExtractor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class VideoExtractorBindingTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val folder = TemporaryFolder()

    @Inject lateinit var extractors: Set<@JvmSuppressWildcards VideoExtractor>
    @Inject lateinit var videos: VideoCommands
    @Inject lateinit var resources: ReleaseProcessResources

    @Before
    fun inject() {
        TestStorage.current.set(AppStorage.temp(folder.newFolder()))
        hilt.inject()
    }

    @After
    fun release() {
        resources.release()
        TestStorage.current.set(null)
    }

    @Test
    fun thePageExtractorsAreBoundAndSavingIsAvailable() {
        assertEquals(setOf("generic", "tiktok", "facebook", "reddit"), extractors.map { it.id }.toSet())
        assertEquals(videos, videos)
    }
}
