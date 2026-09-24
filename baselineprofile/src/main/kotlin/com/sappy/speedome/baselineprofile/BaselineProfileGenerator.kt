package com.sappy.speedome.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup plus the paths people use first: every Speed theme (gauge drawing, the map, the nerd
 * page), then Trips and Settings. The resulting profile ships in the APK via ProfileInstaller.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.sappy.SpeedoMe", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("Next theme")), 5_000)
        repeat(9) {
            device.findObject(By.desc("Next theme"))?.click()
            device.waitForIdle()
            Thread.sleep(600)
        }
        device.findObject(By.text("Trips"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Settings"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Speed"))?.click()
        device.waitForIdle()
    }
}
