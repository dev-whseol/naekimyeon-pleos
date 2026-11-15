package com.naekimyeon.pleos.platform

import android.Manifest
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.naekimyeon.pleos.MainActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Activity + permission grant + Android provider + domain + UI. Runs on emulators only.
 * No special production Intent or replacement composable is used.
 */
@RunWith(AndroidJUnit4::class)
class LocationJourneyInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test(timeout = 300_000)
    fun deliveredProviderFixFindsRealParkAndSourceSwitchStopsLocation() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val pkg = context.packageName
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val manualDriving = InstrumentationRegistry.getArguments().getString("manualDriving")?.toBooleanStrictOrNull() == true
        val output = File(context.getExternalFilesDir(null), "preview-qa").apply {
            check(exists() || mkdirs()) { "Cannot create the journey screenshot directory" }
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putBoolean("manualDrivingEnabled", manualDriving)
            putString("recreationKind", "ActivityScenario.recreate; same process, not a cold-process restart")
        })
        val preferences = LocationPreferenceStore(context)
        val previousPreference = preferences.isEnabled()
        val fineAlready = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseAlready = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val previousMock = device.executeShellCommand("appops get $pkg android:mock_location")
        val mockMode = Regex("mock_location: ([a-z_]+)", RegexOption.IGNORE_CASE).find(previousMock)?.groupValues?.get(1) ?: "default"
        preferences.setEnabled(false)
        var providerAdded = false
        fun injectFreshSeoulLocation() {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 37.5430717
                longitude = 127.0417982
                accuracy = 15f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
        }
        fun capture(fileName: String, reportKey: String) {
            val file = File(output, fileName)
            assertTrue("Could not capture $fileName", device.takeScreenshot(file))
            publishScreenshot(context, file, reportKey)
        }
        fun tap(text: String) {
            var item = device.wait(Until.findObject(By.pkg(pkg).text(text)), 1_000)
            repeat(7) {
                if (item == null) {
                    device.findObject(By.pkg(pkg).scrollable(true))?.scroll(Direction.DOWN, .7f)
                    item = device.wait(Until.findObject(By.pkg(pkg).text(text)), 500)
                }
            }
            checkNotNull(item) { "Missing real Activity control: $text" }.click()
            device.waitForIdle(400)
        }
        try {
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.ACCESS_COARSE_LOCATION)
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION)
            device.executeShellCommand("appops set $pkg android:mock_location allow")
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            providerAdded = true
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            injectFreshSeoulLocation()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("어디 가지?")), 15_000))
                tap("현재 위치 사용")
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("현재 위치 기준으로 찾았어요")), 25_000))
                assertTrue(device.hasObject(By.pkg(pkg).textContains("테스트 위치")) ||
                    device.hasObject(By.pkg(pkg).textContains("시험 위치")) || device.hasObject(By.pkg(pkg).textContains("에뮬레이터 위치")))
                tap("앱을 열 때 내 위치 사용")
                instrumentation.waitForIdleSync()
                assertTrue(preferences.isEnabled())
                // Recreate the actual Activity: no location values were saved, but the explicit bool can resume.
                scenario.recreate()
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("현재 위치 기준으로 찾았어요")), 25_000))
                tap("추천 장소 보기")
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("서울숲")), 5_000))
                assertFalse(device.hasObject(By.pkg(pkg).text("강변 산책길")))
                capture("nearby-results.png", "nearbyResultsScreenshotUri")
                tap("서울숲")
                tap("내비로 연결")
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("대표 위치 보내기")), 5_000))
                capture("representative-confirmation.png", "representativeConfirmationScreenshotUri")
                tap("둘러보기 계속")
                if (manualDriving) {
                    verifyManualDrivingRefresh(scenario, device, pkg, ::injectFreshSeoulLocation, ::capture)
                }
                device.pressBack()
                device.pressBack()
                tap("샘플 둘러보기")
                instrumentation.waitForIdleSync()
                assertFalse(preferences.isEnabled())
                tap("내 위치로 공원 보기")
                assertNotNull(device.wait(Until.findObject(By.pkg(pkg).text("지금 위치에서 찾아볼까요?")), 5_000))
                assertFalse(device.hasObject(By.pkg(pkg).text("현재 위치 기준으로 찾았어요")))
            }
        } finally {
            if (providerAdded) runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            device.executeShellCommand("appops set $pkg android:mock_location $mockMode")
            preferences.setEnabled(previousPreference)
            // Grant restoration is run by the host after instrumentation exits: revoking in this
            // process can kill the runner before it records its result.
            val result = android.os.Bundle().apply {
                putString("locationPermissionRestoration", "fineWasGranted=$fineAlready,coarseWasGranted=$coarseAlready")
            }
            instrumentation.sendStatus(0, result)
        }
    }

    /** The host changes D/P through the real Virtual Console; this test never changes vehicle policy. */
    private fun verifyManualDrivingRefresh(
        scenario: ActivityScenario<MainActivity>,
        device: UiDevice,
        packageName: String,
        injectFreshLocation: () -> Unit,
        capture: (String, String) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val drivingCandidate = By.pkg(packageName).text("서울숲 안내")
        fun assertNoPermissionDialog() {
            assertFalse("Location permission UI appeared while driving",
                device.hasObject(By.pkg("com.android.permissioncontroller")) ||
                    device.hasObject(By.pkg("com.google.android.permissioncontroller")))
        }
        fun awaitDrivingPanel(timeoutMillis: Long): Boolean {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            var nextFix = 0L
            while (SystemClock.elapsedRealtime() < deadline) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextFix) {
                    injectFreshLocation()
                    nextFix = now + 30_000L
                }
                assertNoPermissionDialog()
                if (device.hasObject(drivingCandidate)) return true
                SystemClock.sleep(250L)
            }
            return false
        }
        var primaryFailure: Throwable? = null
        instrumentation.sendStatus(0, Bundle().apply {
            putString("awaitingVehicleGear", "D")
            putString("drivingTestStage", "Change gear with the real Virtual Console; waiting up to 60 seconds")
        })
        try {
            assertTrue("The real driving panel did not appear after the host's D transition", awaitDrivingPanel(60_000L))
            capture("nearby-driving-before-recreate.png", "drivingBeforeRecreateScreenshotUri")

            injectFreshLocation()
            val pidBefore = android.os.Process.myPid()
            // Recreates the real Activity in its current process. It is not a cold-process test.
            scenario.recreate()
            assertTrue("Remembered opt-in did not resume location after Activity recreation in D", awaitDrivingPanel(25_000L))
            assertNoPermissionDialog()
            assertEquals("This test is explicitly same-process Activity recreation", pidBefore, android.os.Process.myPid())
            capture("nearby-driving-after-recreate.png", "drivingAfterRecreateScreenshotUri")
            instrumentation.sendStatus(0, Bundle().apply {
                putBoolean("activityRecreatedWhileDriving", true)
                putBoolean("coldProcessRestartTested", false)
                putBoolean("permissionDialogObserved", false)
            })

            val started = SystemClock.elapsedRealtime()
            val deadline = started + 125_000L
            var nextFix = started
            var nextProgress = started + 30_000L
            var updates = 0
            var observations = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextFix) {
                    injectFreshLocation()
                    updates++
                    nextFix += 30_000L
                }
                assertNoPermissionDialog()
                assertTrue("Nearby driving candidate disappeared before the 125-second refresh observation completed",
                    device.wait(Until.hasObject(drivingCandidate), 2_000L))
                observations++
                if (now >= nextProgress) {
                    instrumentation.sendStatus(0, Bundle().apply {
                        putLong("drivingObservationElapsedMillis", now - started)
                        putInt("freshMockProviderUpdates", updates)
                    })
                    nextProgress += 30_000L
                }
                SystemClock.sleep(minOf(1_000L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)))
            }
            val observedMillis = SystemClock.elapsedRealtime() - started
            assertTrue("The observation must exceed the 120-second fix lifetime", observedMillis >= 125_000L)
            assertTrue("Fresh mock fixes must be delivered every 30 seconds during the observation", updates >= 5)
            assertNoPermissionDialog()
            assertTrue(device.hasObject(drivingCandidate))
            capture("nearby-driving-after-125s.png", "drivingAfter125SecondsScreenshotUri")
            instrumentation.sendStatus(0, Bundle().apply {
                putBoolean("drivingRefreshBeyondTwoMinutesPassed", true)
                putLong("drivingObservationElapsedMillis", observedMillis)
                putInt("freshMockProviderUpdates", updates)
                putInt("drivingCandidateObservations", observations)
                putLong("candidateVisibilityWaitToleranceMillis", 2_000L)
                putBoolean("coldProcessRestartTested", false)
            })
        } catch (failure: Throwable) {
            primaryFailure = failure
            runCatching { capture("nearby-driving-failure.png", "drivingFailureScreenshotUri") }
            throw failure
        } finally {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("awaitingVehicleGear", "P")
                putString("drivingTestStage", "Restore P with the real Virtual Console before sample-mode cleanup")
            })
            val parked = device.wait(Until.hasObject(By.pkg(packageName).text("내비로 연결")), 60_000L)
            if (parked) {
                capture("nearby-parked-after-driving.png", "parkedAfterDrivingScreenshotUri")
            } else {
                val restoreFailure = AssertionError("The unrestricted real-place detail screen did not return after the host's P transition")
                if (primaryFailure != null) primaryFailure.addSuppressed(restoreFailure) else throw restoreFailure
            }
        }
    }

    /** Publishes only test-created screenshots; origin coordinates are never in the app UI. */
    private fun publishScreenshot(context: Context, file: File, reportKey: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${file.nameWithoutExtension}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NaekimyeonQA/LocationJourney")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, values)) { "MediaStore returned no screenshot URI" }
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString(reportKey, uri.toString())
                putString("locationJourneyCapture", file.name)
                putString("locationJourneyScreenshotUri", uri.toString())
            })
        } catch (failure: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }
}
