package com.dantech.skimbrowser.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBenchmarks {

    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun navigationAndTabsAndSheet() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Get started")), 20_000)
        device.findObject(By.text("Get started"))?.click()
        device.wait(Until.hasObject(By.text("Start browsing")), 20_000)
        device.findObject(By.text("Start browsing"))?.click()
        device.wait(Until.hasObject(By.text("Shortcuts")), 20_000)
        val tabs = device.findObject(By.descContains("Tabs"))
        tabs?.click()
        device.wait(Until.hasObject(By.text("New tab")), 20_000)
        device.pressBack()
        val ask = device.findObject(By.descContains("Ask Skim"))
        ask?.click()
        device.waitForIdle()
        device.pressBack()
    }

    private companion object {
        const val PACKAGE = "com.dantech.skimbrowser"
    }
}
