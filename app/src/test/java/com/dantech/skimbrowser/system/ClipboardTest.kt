package com.dantech.skimbrowser.system

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Clipboard confirmation and the sensitive flag for an incognito link. */
@RunWith(AndroidJUnit4::class)
class ClipboardTest {

    @Test
    @Config(sdk = [32])
    fun android12AsksTheAppToConfirmAndMarksASensitiveClip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(Clipboard.copy(context, "https://secret.example", sensitive = true))
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals("https://secret.example", clip?.getItemAt(0)?.text?.toString())
        assertEquals("text", clip?.description?.label?.toString())
        assertEquals(true, clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test
    @Config(sdk = [33])
    fun android13ConfirmsTheCopyItself() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(Clipboard.copy(context, "https://example.com", sensitive = false))
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals("https://example.com", clip?.getItemAt(0)?.text?.toString())
        assertFalse(clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
    }
}
