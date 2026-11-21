package com.naekimyeon.pleos.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.naekimyeon.pleos.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real release UI: drawing cannot escape the selected travel budget or launch navigation. */
@RunWith(AndroidJUnit4::class)
class SceneDrawInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Test(timeout = 45_000)
    fun drawUsesOnlyEligibleSceneAndRequiresExplicitDetailChoice() {
        ActivityScenario.launch(MainActivity::class.java).use {
            visible("어디 가지?", 5_000)
            tap("샘플 둘러보기")
            tap("조건 더하기")
            tap("머리 좀 비우고 싶어")
            tap("바람 쐬기")
            tap("45분")
            tap("출발한 곳으로 돌아오기")
            tap("직접 고르기")
            visible("지금 가볼 만한 곳")
            visible("바람 쐬기  ·  45분 안에 돌아오기")
            visible("약 10분 머물 수 있어요")
            tap("하나만 바로 추천받기")
            visible("강변 산책길 자세히 보기")
            visible("선택 가능한 1곳")
            assertFalse("A filtered-out cafe must never be drawn", device.hasObject(By.pkg(packageName).text("창가의 작은 카페 자세히 보기")))
            assertFalse("A filtered-out park must never be drawn", device.hasObject(By.pkg(packageName).text("나무 그늘 쉼터 자세히 보기")))
            assertTrue("The draw must expose its sample source", device.hasObject(By.pkg(packageName).text("샘플 장소 · 예상 정보 · 실시간 정보 아님")))
            assertEquals("Drawing must not launch navigation", packageName, device.currentPackageName)
            tap("강변 산책길 자세히 보기")
            visible("목적지 확인")
            assertEquals("Opening detail must not launch navigation", packageName, device.currentPackageName)
        }
    }

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

    private fun visible(text: String, timeout: Long = 2_000): UiObject2 = checkNotNull(
        device.wait(Until.findObject(By.pkg(packageName).text(text)), timeout)
    ) { "Expected app text: $text. Run on parked Pleos with vehicle restrictions available." }
}
