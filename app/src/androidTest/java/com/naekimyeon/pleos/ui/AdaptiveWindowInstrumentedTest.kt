package com.naekimyeon.pleos.ui

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.naekimyeon.pleos.MainActivity
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requests real platform window changes; never substitutes a fake Compose constraint or am task resize.
 * Run on a parked Pleos emulator. A vendor that ignores orientation requests must fail the width check.
 * The two screenshots and measurement report are retained even when resizing is unsupported.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveWindowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Test(timeout = 60_000)
    fun realOrientationWindowsPreserveExplicitChoices() {
        val output = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir("qa")), "adaptive").apply {
            check(exists() || mkdirs()) { "Cannot create QA artifact directory: $absolutePath" }
        }
        val report = JSONObject()
            .put("mechanism", "Activity.requestedOrientation; actual android.R.id.content size")
            .put("stateValidation", "Expected results summary and 10-minute stay after each return/resize")
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { originalOrientation = it.requestedOrientation }
            requireVisible("어디 가지?", 5_000)
            tap("샘플 둘러보기")
            tap("이동 조건 더하기")
            tap("바람 쐬기")
            tap("45분")
            tap("출발한 곳으로 돌아오기")
            tap("추천 장소 보기")
            requireVisible("지금 가볼 만한 곳", 2_000)
            assertTrue("The explicit need and round-trip budget must reach the results screen", device.hasObject(
                By.pkg(packageName).text("바람 쐬기  ·  45분 안에 돌아오기")
            ))
            assertTrue("The 45-minute round-trip example must leave 10 minutes at the river", device.hasObject(
                By.pkg(packageName).text("약 10분 머물 수 있어요")
            ))
            val back = device.wait(Until.findObject(By.pkg(packageName).desc("이전 화면")), 2_000)
            checkNotNull(back) { "The app's back control was not accessible" }.click()
            requireVisible("어디 가지?", 2_000)
            assertChoicesRetained("after returning from results")

            val initial = snapshot(scenario)
            report.put("initial", initial.json())

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            val portrait = waitForWidthChange(scenario, initial.width, 6_000)
            report.put("portraitRequested", portrait.json())
            val portraitFile = File(output, "adaptive_portrait_requested.png")
            val portraitSaved = device.takeScreenshot(portraitFile)
            report.put("portraitScreenshotSaved", portraitSaved)
            if (portraitSaved) publishScreenshot(portraitFile, "portraitScreenshotUri", report)
            assertChoicesRetained("after the portrait request")

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val landscape = waitForWidthChange(scenario, portrait.width, 6_000)
            report.put("landscapeRequested", landscape.json())
            val landscapeFile = File(output, "adaptive_landscape_requested.png")
            val landscapeSaved = device.takeScreenshot(landscapeFile)
            report.put("landscapeScreenshotSaved", landscapeSaved)
            if (landscapeSaved) publishScreenshot(landscapeFile, "landscapeScreenshotUri", report)
            assertChoicesRetained("after the landscape request")

            val actualDistinctWidths = portrait.width + 64 < landscape.width
            report.put("actualDistinctWidths", actualDistinctWidths)
            report.put("choicesRetained", true)
            report.put("passed", actualDistinctWidths && portraitSaved && landscapeSaved)
            assertTrue("Both actual-display screenshots must be saved", portraitSaved && landscapeSaved)
            assertTrue(
                "Pleos did not provide distinct portrait/landscape activity widths: initial=$initial, " +
                    "portrait=$portrait, landscape=$landscape. Orientation flags alone are not evidence of resize. " +
                    "See ${output.absolutePath}/adaptive_window_measurements.json",
                actualDistinctWidths,
            )
        } catch (failure: Throwable) {
            report.put("passed", false).put("failure", failure.message ?: failure.javaClass.simpleName)
            runCatching {
                val failureFile = File(output, "adaptive_failure.png")
                if (device.takeScreenshot(failureFile)) publishScreenshot(failureFile, "failureScreenshotUri", report)
            }
            runCatching { device.dumpWindowHierarchy(File(output, "adaptive_failure_hierarchy.xml")) }
            throw failure
        } finally {
            try {
                try {
                    scenario.onActivity { it.requestedOrientation = originalOrientation }
                } finally {
                    scenario.close()
                }
            } finally {
                runCatching { File(output, "adaptive_window_measurements.json").writeText(report.toString(2)) }
                // ADB cannot read another Android user's app-private external directory on Pleos.
                // Return compact, non-sensitive measurements and MediaStore URIs through instrumentation.
                instrumentation.sendStatus(0, Bundle().apply { putString("adaptiveWindowReport", report.toString()) })
            }
        }
    }

    private fun tap(text: String) {
        requireVisible(text, 2_000).click()
        device.waitForIdle(500)
    }

    private fun requireVisible(text: String, timeout: Long): UiObject2 = checkNotNull(
        device.wait(Until.findObject(By.pkg(packageName).text(text)), timeout)
    ) { "Expected app text was not visible: $text. The vehicle must be parked and restrictions available." }

    private fun assertChoicesRetained(stage: String) {
        // Behavioral evidence avoids depending on how Compose exports selected semantics to accessibility.
        tap("추천 장소 보기")
        assertTrue("Need, budget or explicit return choice was lost $stage", device.wait(
            Until.hasObject(By.pkg(packageName).text("바람 쐬기  ·  45분 안에 돌아오기")), 2_000
        ))
        assertTrue("The retained choices did not produce the expected 10-minute stay $stage", device.wait(
            Until.hasObject(By.pkg(packageName).text("약 10분 머물 수 있어요")), 2_000
        ))
        val back = device.wait(Until.findObject(By.pkg(packageName).desc("이전 화면")), 2_000)
        checkNotNull(back) { "The app's back control was not accessible $stage" }.click()
        requireVisible("어디 가지?", 2_000)
    }

    /** Scoped MediaStore publication of test-created images only; no release permission or provider change.
     * https://developer.android.com/training/data-storage/shared/media#add-item
     */
    private fun publishScreenshot(file: File, reportKey: String, report: JSONObject) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            report.put(reportKey + "Error", "Scoped image publication requires Android 10+")
            return
        }
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${file.nameWithoutExtension}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NaekimyeonQA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        runCatching {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = checkNotNull(resolver.insert(collection, values)) { "MediaStore returned no image URI" }
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                report.put(reportKey, uri.toString())
            } catch (failure: Throwable) {
                // Only clean up this test's newly inserted, incomplete item.
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        }.onFailure { report.put(reportKey + "Error", it.message ?: it.javaClass.simpleName) }
    }

    private fun waitForWidthChange(
        scenario: ActivityScenario<MainActivity>, previousWidth: Int, timeout: Long,
    ): WindowSnapshot {
        val until = SystemClock.elapsedRealtime() + timeout
        var latest = snapshot(scenario)
        var stableWidth = -1
        var consecutiveStableSamples = 0
        do {
            latest = snapshot(scenario)
            if (latest.width != previousWidth && latest.width > 0 && latest.height > 0) {
                if (latest.width == stableWidth) consecutiveStableSamples++ else consecutiveStableSamples = 1
                stableWidth = latest.width
                if (consecutiveStableSamples >= 3) {
                    device.waitForIdle(500)
                    return snapshot(scenario)
                }
            }
            SystemClock.sleep(150)
        } while (SystemClock.elapsedRealtime() < until)
        // Returning unchanged measurements is intentional: the final assertion must fail, with artifacts.
        return latest
    }

    private fun snapshot(scenario: ActivityScenario<MainActivity>): WindowSnapshot {
        var result: WindowSnapshot? = null
        scenario.onActivity { activity ->
            val content = activity.findViewById<View>(android.R.id.content)
            val position = IntArray(2)
            content.getLocationOnScreen(position)
            result = WindowSnapshot(content.width, content.height, position[0], position[1],
                activity.resources.configuration.orientation, activity.requestedOrientation)
        }
        return requireNotNull(result)
    }

    private data class WindowSnapshot(
        val width: Int, val height: Int, val x: Int, val y: Int,
        val configurationOrientation: Int, val requestedOrientation: Int,
    ) {
        fun json(): JSONObject = JSONObject()
            .put("widthPx", width).put("heightPx", height).put("xPx", x).put("yPx", y)
            .put("configurationOrientation", configurationOrientation)
            .put("requestedOrientation", requestedOrientation)
    }
}
