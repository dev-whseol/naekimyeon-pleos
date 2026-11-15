package com.naekimyeon.pleos.ui

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
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
import com.naekimyeon.pleos.platform.LocationPrecision
import com.naekimyeon.pleos.platform.LocationState
import com.naekimyeon.pleos.platform.LocationStatus
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled UI-state tests; real runtime permission and provider delivery are separately exercised on device. */
@RunWith(AndroidJUnit4::class)
class NearbyFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Test(timeout = 60_000)
    fun realUnknownTravelRemainsVisibleAndChangedLocationInvalidatesConfirmations() {
        val location = mutableStateOf(ready())
        val restricted = mutableStateOf(false)
        val navigations = AtomicInteger()
        val requests = AtomicInteger()
        launch(location, restricted, navigations, requests).use { scenario ->
            visible("어디 가지?")
            tap("45분")
            tap("출발한 곳으로 돌아오기")
            tap("추천 장소 보기")
            visible("지금 가볼 만한 곳")
            visible("이동 시간 확인 필요")
            assertFalse(device.hasObject(By.pkg(packageName).textContains("분 머물 수 있어요")))
            assertFalse(device.hasObject(By.pkg(packageName).text("가는 길 약 12분")))
            assertEquals(0, navigations.get())

            tap("하나만 바로 추천받기")
            assertNotNull(device.wait(Until.findObject(By.pkg(packageName).desc("추천 창 닫기")), 2_000))
            scenario.onActivity { location.value = ready(GeoPoint(37.5434, 127.0420)) }
            assertTrue(device.wait(Until.gone(By.pkg(packageName).desc("추천 창 닫기")), 2_000))
            assertEquals(0, navigations.get())

            tap("서울숲")
            visible("서울숲")
            tap("내비로 연결")
            visible("대표 위치 보내기")
            assertTrue(device.hasObject(By.pkg(packageName).textContains("공원 대표 위치")))
            scenario.onActivity {
                location.value = LocationState(LocationStatus.STALE, message = "확인된 위치 정보가 오래되어 지금 위치를 다시 불러와야 해요.")
            }
            assertTrue(device.wait(Until.gone(By.pkg(packageName).text("대표 위치 보내기")), 2_000))
            assertEquals(0, navigations.get())
            assertFalse(device.hasObject(By.pkg(packageName).text("내비로 연결")))
            // Expiry never silently switches back to sample data; the user must choose it.
            assertFalse(device.hasObject(By.pkg(packageName).text("샘플 장소 · 예상 정보 · 실시간 정보 아님")))
            tap("샘플 둘러보기")
            visible("샘플")
            assertEquals(0, requests.get())
        }
    }

    @Test(timeout = 45_000)
    fun unsupportedAreaAndPermissionFailureCannotExposeSeoulOrPermissionUiWhileDriving() {
        val location = mutableStateOf(ready(GeoPoint(35.1796, 129.0756)))
        val restricted = mutableStateOf(false)
        val navigations = AtomicInteger()
        val requests = AtomicInteger()
        launch(location, restricted, navigations, requests).use { scenario ->
            visible("이 근처는 아직 준비 중이에요")
            assertFalse(device.hasObject(By.pkg(packageName).text("서울숲")))
            assertFalse(device.hasObject(By.pkg(packageName).text("월드컵공원")))
            scenario.onActivity {
                location.value = LocationState(LocationStatus.PERMISSION_BLOCKED, message = "위치 권한이 차단되어 있으니 설정에서 권한을 허용해 주세요.")
            }
            tap("설정에서 권한 켜기")
            instrumentation.waitForIdleSync()
            assertEquals(1, requests.get())
            assertEquals(0, navigations.get())
            scenario.onActivity { restricted.value = true }
            assertTrue(device.wait(Until.gone(By.pkg(packageName).text("설정에서 권한 켜기")), 2_000))
            assertFalse(device.hasObject(By.pkg(packageName).text("샘플 둘러보기")))
            assertFalse(device.hasObject(By.pkg(packageName).text("서울숲 안내")))
            assertEquals(1, requests.get())
            assertEquals(0, navigations.get())
        }
    }

    private fun launch(
        location: MutableState<LocationState>, restricted: MutableState<Boolean>,
        navigations: AtomicInteger, requests: AtomicInteger,
    ): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario ->
        scenario.onActivity { activity ->
            activity.setContent {
                NaekimyeonApp(
                    navigationStatus = "", onNavigate = { navigations.incrementAndGet() },
                    restricted = restricted.value, drivingAvailable = true,
                    locationState = location.value, onRequestLocation = { requests.incrementAndGet() },
                )
            }
        }
    }

    private fun ready(point: GeoPoint = GeoPoint(37.5430717, 127.0417982)) = LocationState(
        LocationStatus.READY,
        LocationFix(point, 35f, SystemClock.elapsedRealtime(), isMock = true),
        LocationPrecision.PRECISE,
        "테스트 위치를 확인했어요",
    )

    private fun visible(text: String): UiObject2 = checkNotNull(
        device.wait(Until.findObject(By.pkg(packageName).text(text)), 3_000)
    ) { "Expected app text: $text" }

    private fun tap(text: String) {
        var target = device.wait(Until.findObject(By.pkg(packageName).text(text)), 1_000)
        repeat(5) {
            if (target == null) {
                device.findObject(By.pkg(packageName).scrollable(true))?.scroll(Direction.DOWN, .65f)
                target = device.wait(Until.findObject(By.pkg(packageName).text(text)), 600)
            }
        }
        checkNotNull(target) { "Expected app control: $text" }.click()
        device.waitForIdle(400)
    }
}
