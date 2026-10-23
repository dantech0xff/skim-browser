package com.dantech.skimbrowser.system

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** Startup deletion of shared images. */
@RunWith(AndroidJUnit4::class)
class ShareFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun purgeRemovesIncognitoSharesAndNormalSharesOlderThanADay() {
        val cache = folder.newFolder("cache")
        val shared = File(cache, "shared")
        val incognito = File(shared, "incognito")
        incognito.mkdirs()
        val secret = File(incognito, "secret.png").apply { writeBytes(byteArrayOf(1)) }
        val old = File(shared, "old.png").apply {
            writeBytes(byteArrayOf(2))
            setLastModified(1_000L)
        }
        val now = 2_000_000_000_000L
        val fresh = File(shared, "fresh.png").apply {
            writeBytes(byteArrayOf(3))
            setLastModified(now - 3_600_000L)
        }

        purgeOldShares(cache, now = now)

        assertFalse(secret.exists())
        assertFalse(incognito.exists())
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
