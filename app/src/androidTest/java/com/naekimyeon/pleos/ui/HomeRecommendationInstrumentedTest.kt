package com.naekimyeon.pleos.ui

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.naekimyeon.pleos.MainActivity
import com.naekimyeon.pleos.domain.GeoPoint
import com.naekimyeon.pleos.domain.LocationFix
import com.naekimyeon.pleos.domain.NearbyPlaces
import com.naekimyeon.pleos.platform.LocationPrecision
import com.naekimyeon.pleos.platform.LocationState
import com.naekimyeon.pleos.platform.LocationStatus
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** UI continuation contract with controlled provider states; actual runtime permission is tested separately. */
@RunWith(AndroidJUnit4::class)
class HomeRecommendationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName
    private val origin = GeoPoint(37.5430717, 127.0417982)

    @Test(timeout = 60_000)
    fun explicitHomeTapContinuesAfterLocationAndCyclesEveryEligiblePlace() {
        val location = mutableStateOf(LocationState())
        val restricted = mutableStateOf(false)
        val requests = AtomicInteger()
        val navigations = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NaekimyeonApp(
                        navigationStatus = "", onNavigate = { navigations.incrementAndGet() },
                        restricted = restricted.value, drivingAvailable = true,
                        locationState = location.value,
                        onRequestLocation = { requests.incrementAndGet(); location.value = LocationState(LocationStatus.REQUESTING_PERMISSION) },
                    )
                }
            }
            tap("한 곳 골라줘")
            assertEquals(1, requests.get())
            assertFalse(device.hasObject(By.pkg(packageName).text("한 곳 골랐어요")))
            scenario.onActivity { location.value = LocationState(LocationStatus.LOCATING) }
            scenario.onActivity { location.value = ready() }
            visible("한 곳 골랐어요")
            val expected = NearbyPlaces.find(origin)
            val seen = mutableListOf<String>()
            repeat(expected.size) { index ->
                val shown = expected.filter { device.hasObject(By.pkg(packageName).text("${it.title} 자세히 보기")) }
                assertEquals("The dialog must contain exactly one eligible destination", 1, shown.size)
                assertFalse("No candidate may repeat before all eligible places appear", shown.single().id in seen)
                seen += shown.single().id
                if (index < expected.lastIndex) tap("다른 곳 보기")
            }
            assertEquals(expected.map { it.id }.toSet(), seen.toSet())
            tap("다른 곳 보기")
            val next = expected.single { device.hasObject(By.pkg(packageName).text("${it.title} 자세히 보기")) }
            assertNotEquals(seen.last(), next.id)
            assertEquals(0, navigations.get())
            scenario.onActivity { restricted.value = true }
            assertTrue(device.wait(Until.gone(By.pkg(packageName).text("한 곳 골랐어요")), 2_000))
            scenario.onActivity { restricted.value = false }
            instrumentation.waitForIdleSync()
            assertFalse("Returning to P must not resurrect the recommendation dialog", device.hasObject(By.pkg(packageName).text("한 곳 골랐어요")))
            assertEquals(0, navigations.get())
        }
    }

    @Test(timeout = 60_000)
    fun denialAndEditedTextDiscardTheOldContinuation() {
        val location = mutableStateOf(LocationState())
        val requests = AtomicInteger()
        val navigations = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NaekimyeonApp(
                        navigationStatus = "", onNavigate = { navigations.incrementAndGet() }, restricted = false,
                        drivingAvailable = true, locationState = location.value,
                        onRequestLocation = { requests.incrementAndGet(); location.value = LocationState(LocationStatus.REQUESTING_PERMISSION) },
                    )
                }
            }
            tap("한 곳 골라줘")
            scenario.onActivity { location.value = LocationState(LocationStatus.PERMISSION_DENIED) }
            visible("위치를 확인한 뒤 다시 골라 주세요.")
            scenario.onActivity { location.value = ready() }
            instrumentation.waitForIdleSync()
            assertFalse("Late data after a denied request must not reopen a recommendation", device.hasObject(By.pkg(packageName).text("한 곳 골랐어요")))

            scenario.onActivity { location.value = LocationState(LocationStatus.STALE) }
            tap("한 곳 골라줘")
            tap("조건 더하기")
            val field = findEditable()
            field.text = "아직 어떻게 보낼지 모르겠어요"
            instrumentation.waitForIdleSync()
            scenario.onActivity { location.value = ready() }
            instrumentation.waitForIdleSync()
            assertFalse("Typed changes must cancel, not re-interpret, the previous request", device.hasObject(By.pkg(packageName).text("한 곳 골랐어요")))
            assertEquals(2, requests.get())
            assertEquals(0, navigations.get())
        }
    }

    private fun ready() = LocationState(LocationStatus.READY,
        LocationFix(origin, 35f, SystemClock.elapsedRealtime(), isMock = true), LocationPrecision.PRECISE)

    private fun visible(text: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.pkg(packageName).text(text)), 3_000))

    private fun tap(text: String) {
        var target = device.wait(Until.findObject(By.pkg(packageName).text(text)), 700)
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(5) {
                if (target == null) {
                    device.findObject(By.pkg(packageName).scrollable(true))?.scroll(direction, .7f)
                    target = device.wait(Until.findObject(By.pkg(packageName).text(text)), 350)
                }
            }
        }
        checkNotNull(target) { "Expected app control: $text" }.click()
        device.waitForIdle(400)
    }

    private fun findEditable(): UiObject2 {
        var field = device.findObject(By.pkg(packageName).clazz("android.widget.EditText"))
        repeat(5) {
            if (field == null) {
                device.findObject(By.pkg(packageName).scrollable(true))?.scroll(Direction.DOWN, .7f)
                field = device.wait(Until.findObject(By.pkg(packageName).clazz("android.widget.EditText")), 350)
            }
        }
        return checkNotNull(field) { "Optional feeling input must become available after opening conditions" }
    }
}
