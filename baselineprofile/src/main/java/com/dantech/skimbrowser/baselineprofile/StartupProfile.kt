package com.dantech.skimbrowser.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Get started")), 15_000)
        device.findObject(By.text("Get started"))?.click()
        device.wait(Until.hasObject(By.text("Start browsing")), 15_000)
        device.findObject(By.text("Start browsing"))?.click()
        device.wait(Until.hasObject(By.text("Shortcuts")), 15_000)
    }

    private companion object {
        const val PACKAGE = "com.dantech.skimbrowser"
    }
}
