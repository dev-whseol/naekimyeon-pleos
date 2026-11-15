package com.naekimyeon.pleos.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

enum class SafetyCapability { CONNECTING, AVAILABLE, UNAVAILABLE, PERMISSION_DENIED }

data class DrivingSafetyState(
    val capability: SafetyCapability,
    val restricted: Boolean = true,
    val activeRestrictions: Int = CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED,
    val message: String = when (capability) {
        SafetyCapability.CONNECTING -> "차량 안전 상태 확인 중"
        SafetyCapability.AVAILABLE -> if (restricted) "차량 이용 제한 중" else "상세 조작 가능 · 차량 이용 제한 없음"
        SafetyCapability.UNAVAILABLE -> "차량 안전 상태를 확인할 수 없어 간단한 화면을 표시해요"
        SafetyCapability.PERMISSION_DENIED -> "차량 안전 상태 접근 권한을 사용할 수 없어요"
    }
)

/** Fail-closed AAOS restrictions; never infer a parked state from speed, gear or missing data. */
class DrivingSafetyAdapter(context: Context, private val onState: (DrivingSafetyState) -> Unit) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var closed = false
    private var started = false
    private var connectionTimeout: Runnable? = null

    fun start() = onMain {
        if (closed || started) return@onMain
        started = true
        publish(DrivingSafetyState(SafetyCapability.CONNECTING))
        if (Build.VERSION.SDK_INT < 29 ||
            !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        ) {
            unavailable(false)
            return@onMain
        }
        connectionTimeout = Runnable {
            connectionTimeout = null
            if (!closed && manager == null) unavailable(false)
        }.also { main.postDelayed(it, 8_000L) }
        try {
            car = Car.createCar(appContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                if (closed) return@createCar
                if (!ready) {
                    manager = null
                    unavailable(false)
                } else {
                    try {
                        val next = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                        if (next == null) {
                            unavailable(false)
                        } else {
                            manager?.let { runCatching { it.unregisterListener() } }
                            manager = next
                            clearConnectionTimeout()
                            next.registerListener { restrictions ->
                                onMain { if (!closed) applyRestrictions(restrictions) }
                            }
                            applyRestrictions(next.currentCarUxRestrictions)
                        }
                    } catch (error: SecurityException) {
                        unavailable(true)
                    } catch (error: Exception) {
                        unavailable(false)
                    }
                }
            }
        } catch (error: SecurityException) {
            unavailable(true)
        } catch (error: Exception) {
            unavailable(false)
        } catch (error: LinkageError) {
            unavailable(false)
        }
    }

    private fun applyRestrictions(restrictions: CarUxRestrictions) {
        val restricted = restrictions.isRequiresDistractionOptimization
        publish(
            DrivingSafetyState(
                capability = SafetyCapability.AVAILABLE,
                restricted = restricted,
                activeRestrictions = restrictions.activeRestrictions,
                message = if (restricted) "차량 이용 제한 중 · 간단한 안내만 표시해요" else "상세 조작 가능 · 차량 이용 제한 없음"
            )
        )
    }

    private fun unavailable(permissionDenied: Boolean) {
        clearConnectionTimeout()
        publish(
            DrivingSafetyState(
                capability = if (permissionDenied) SafetyCapability.PERMISSION_DENIED else SafetyCapability.UNAVAILABLE,
                message = if (permissionDenied) "차량 안전 상태 접근 권한을 사용할 수 없어요" else "차량 안전 상태를 확인할 수 없어 간단한 화면을 표시해요"
            )
        )
    }

    private fun clearConnectionTimeout() {
        connectionTimeout?.let(main::removeCallbacks)
        connectionTimeout = null
    }

    private fun publish(state: DrivingSafetyState) {
        if (!closed) onState(state)
    }

    override fun close() = onMain {
        if (closed) return@onMain
        closed = true
        clearConnectionTimeout()
        manager?.let { runCatching { it.unregisterListener() } }
        manager = null
        car?.let { runCatching { it.disconnect() } }
        car = null
        main.removeCallbacksAndMessages(null)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
