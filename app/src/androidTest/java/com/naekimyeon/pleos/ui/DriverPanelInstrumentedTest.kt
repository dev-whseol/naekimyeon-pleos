package com.naekimyeon.pleos.ui

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.naekimyeon.pleos.MainActivity
import com.naekimyeon.pleos.domain.IncomingRequest
import com.naekimyeon.pleos.domain.MoodIntent
import com.naekimyeon.pleos.domain.Place
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** UI contract test with controlled safety inputs; actual CarUxRestrictions D/P is verified separately.
 * No production entry point, gear override, exported component or navigation service is added.
 */
@RunWith(AndroidJUnit4::class)
class DriverPanelInstrumentedTest {
    @Test(timeout = 45_000)
    fun knownDriverModeShowsOneCandidateAndUnknownStateHasNoActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val packageName = instrumentation.targetContext.packageName
        val restricted = mutableStateOf(false)
        val available = mutableStateOf(true)
        val navigationCount = AtomicInteger(0)
        val destination = AtomicReference<Place?>(null)
        val appliedCandidate = AtomicReference<Place?>(null)
        val incoming = mutableStateOf<IncomingRequest?>(null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NaekimyeonApp(
                        navigationStatus = "",
                        onNavigate = { destination.set(it); navigationCount.incrementAndGet() },
                        restricted = restricted.value,
                        drivingAvailable = available.value,
                        restrictionMessage = "차량 상태를 확인할 수 없어요",
                        incomingRequest = incoming.value,
                        onDrivingCandidateApplied = { id, place -> if (id == 9001L) appliedCandidate.set(place) },
                    )
                }
            }
            checkNotNull(device.wait(Until.findObject(By.pkg(packageName).text("샘플 둘러보기")), 5_000)).click()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                restricted.value = true
                incoming.value = IncomingRequest(id = 9001, feeling = MoodIntent.RESET.phrase)
            }
            assertNotNull(device.wait(Until.findObject(By.pkg(packageName).text("강변 산책길 안내")), 5_000))
            assertTrue(device.hasObject(By.pkg(packageName).text("지금 가볼 만한 곳")))
            assertFalse(device.hasObject(By.pkg(packageName).text(MoodIntent.RESET.phrase)))
            assertFalse(device.hasObject(By.pkg(packageName).text("지금 마음을 한마디로")))
            assertFalse(device.hasObject(By.pkg(packageName).text("뜻밖의 한 장 뽑기")))
            assertFalse(device.hasObject(By.pkg(packageName).scrollable(true)))
            assertEquals(0, navigationCount.get())
            repeat(20) { if (appliedCandidate.get() == null) SystemClock.sleep(50) }
            assertEquals("riverside", appliedCandidate.get()?.id)

            device.findObject(By.pkg(packageName).text("다른 곳 보기")).click()
            val next = device.wait(Until.findObject(By.pkg(packageName).text("나무 그늘 쉼터 안내")), 2_000)
            assertNotNull(next)
            assertEquals(0, navigationCount.get())
            next.click()
            instrumentation.waitForIdleSync()
            assertEquals(1, navigationCount.get())
            assertEquals("forest_rest", destination.get()?.id)

            scenario.onActivity { available.value = false }
            assertNotNull(device.wait(Until.findObject(By.pkg(packageName).text("차량 상태를 확인할 수 없어요")), 2_000))
            assertFalse(device.hasObject(By.pkg(packageName).text("다른 곳 보기")))
            assertFalse(device.hasObject(By.pkg(packageName).text("나무 그늘 쉼터 안내")))

            scenario.onActivity { available.value = true; restricted.value = false }
            assertNotNull(device.wait(Until.findObject(By.pkg(packageName).text("목적지 확인")), 3_000))
            assertTrue(device.hasObject(By.pkg(packageName).text("나무 그늘 쉼터")))
            assertEquals(1, navigationCount.get())
        }
    }
}
