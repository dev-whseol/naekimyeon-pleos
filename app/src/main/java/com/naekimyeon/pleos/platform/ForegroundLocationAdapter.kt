package com.naekimyeon.pleos.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.naekimyeon.pleos.domain.GeoPoint
import com.naekimyeon.pleos.domain.LocationFix
import com.naekimyeon.pleos.domain.LocationFixPolicy
import java.util.concurrent.TimeUnit

/**
 * Foreground acquisitions for an explicitly opted-in Activity session. This class never requests permissions,
 * starts a service, writes a fix to storage, logs coordinates, or directly trusts lastKnownLocation.
 */
class ForegroundLocationAdapter(context: Context, private val onState: (LocationState) -> Unit) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val session = LocationSessionGuard()
    private val signals = mutableMapOf<String, CancellationSignal>()
    private val remaining = mutableSetOf<String>()
    private var closed = false
    private var acquiring = false
    private var receiverRegistered = false
    private var timeout: Runnable? = null
    private var expiry: Runnable? = null
    private var state = LocationState()
    private var sawStale = false
    private var sawLowAccuracy = false
    private var sawInvalid = false
    private var requestPrecision = LocationPrecision.NONE

    private val providerChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onMain {
            if (!closed && (acquiring || state.isUsable) && !providersAvailable()) {
                stopAcquisition()
                publishFailure(LocationState(LocationStatus.PROVIDERS_DISABLED, message = "기기 위치 기능이 꺼져 있어요 · 정차 후 설정을 확인해 주세요"))
            }
        }
    }

    fun awaitingPermission() = onMain {
        if (closed) return@onMain
        stopAcquisition()
        clearFix()
        publish(LocationState(LocationStatus.REQUESTING_PERMISSION, message = "현재 위치 권한을 선택해 주세요 · 대략적 위치도 사용할 수 있어요"))
    }

    fun permissionDenied(blocked: Boolean) = onMain {
        if (closed) return@onMain
        stopAcquisition()
        clearFix()
        publish(LocationState(
            if (blocked) LocationStatus.PERMISSION_BLOCKED else LocationStatus.PERMISSION_DENIED,
            message = if (blocked) "위치 권한을 사용할 수 없어요 · 필요하면 기기 설정에서 허용해 주세요" else "위치 권한이 허용되지 않았어요 · 샘플은 계속 볼 수 있어요"
        ))
    }

    /** No permission UI: MainActivity permits opted-in, granted foreground refreshes while driving. */
    fun request(keepCurrent: Boolean = false) = onMain {
        if (closed) return@onMain
        stopAcquisition()
        val precision = permissionPrecision(appContext)
        if (precision == LocationPrecision.NONE) {
            permissionDenied(false)
            return@onMain
        }
        val retained = keepCurrent && state.isUsable && state.precision == precision &&
            LocationFixPolicy.isUsable(state.fix!!, SystemClock.elapsedRealtime())
        if (!retained) {
            clearFix()
            state = LocationState()
        }
        val locationManager = manager
        if (locationManager == null) {
            publishFailure(LocationState(LocationStatus.UNAVAILABLE, message = "이 기기에서 위치 제공자를 사용할 수 없어요"))
            return@onMain
        }
        val providers = try {
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) emptyList()
            else listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, "fused")
                .distinct().filter { it in locationManager.allProviders && locationManager.isProviderEnabled(it) }
        } catch (error: Exception) {
            emptyList()
        }
        if (providers.isEmpty()) {
            val supported = runCatching { locationManager.allProviders.any { it in setOf("gps", "network", "fused") } }.getOrDefault(false)
            publishFailure(LocationState(if (supported) LocationStatus.PROVIDERS_DISABLED else LocationStatus.UNAVAILABLE, precision = precision,
                message = if (supported) "위치 제공자가 꺼져 있어요 · 정차 후 기기 설정을 확인해 주세요" else "이 기기에서 Android 위치 제공자를 사용할 수 없어요"))
            return@onMain
        }
        val token = session.begin() ?: return@onMain
        requestPrecision = precision
        acquiring = true
        sawStale = false
        sawLowAccuracy = false
        sawInvalid = false
        remaining.addAll(providers)
        if (retained) publish(state.copy(isRefreshing = true, refreshIssue = null,
            message = if (state.fix?.isMock == true || state.fix?.isEmulator == true) "시험 위치를 갱신 중이에요 · 실제 차량 위치가 아니에요" else "사용 중인 위치를 갱신하고 있어요"))
        else publish(LocationState(LocationStatus.LOCATING, precision = precision,
            message = if (precision == LocationPrecision.APPROXIMATE) "대략적 현재 위치를 확인하고 있어요" else "현재 위치를 확인하고 있어요"))
        timeout = Runnable {
            if (session.isCurrent(token)) {
                stopAcquisition(keepProviderWatch = state.isUsable)
                publishFailure(LocationState(LocationStatus.TIMED_OUT, precision = permissionPrecision(appContext),
                    message = "현재 위치를 받지 못했어요 · 정차 중 다시 시도하거나 샘플을 선택해 주세요"))
            }
        }.also { main.postDelayed(it, REQUEST_TIMEOUT_MILLIS) }
        try {
            ContextCompat.registerReceiver(appContext, providerChanges,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply { addAction(LocationManager.MODE_CHANGED_ACTION) },
                ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (error: Exception) {
            // The finite acquisition deadline and completion checks still handle provider loss.
        }
        for (provider in providers) {
            if (!session.isCurrent(token)) break
            requestProvider(locationManager, provider, token)
        }
    }

    @SuppressLint("MissingPermission") // Permission is checked immediately above and failures are handled here.
    private fun requestProvider(locationManager: LocationManager, provider: String, token: Long) {
        val signal = CancellationSignal()
        signals[provider] = signal
        try {
            LocationManagerCompat.getCurrentLocation(locationManager, provider, signal,
                ContextCompat.getMainExecutor(appContext)) { location ->
                onMain { onProviderResult(token, provider, location) }
            }
        } catch (error: SecurityException) {
            if (permissionPrecision(appContext) == LocationPrecision.NONE) permissionDenied(false)
            else onProviderResult(token, provider, null)
        } catch (error: Exception) {
            sawInvalid = true
            onProviderResult(token, provider, null)
        }
    }

    private fun onProviderResult(token: Long, provider: String, location: Location?) {
        if (closed || !session.isCurrent(token)) return
        remaining.remove(provider)
        signals.remove(provider)
        val precision = permissionPrecision(appContext)
        if (precision == LocationPrecision.NONE) {
            permissionDenied(false)
            return
        }
        if (precision != requestPrecision) {
            stopAcquisition()
            clearFix()
            publish(LocationState(LocationStatus.STALE, precision = precision,
                message = "위치 권한 범위가 바뀌어 이전 응답을 버렸어요 · 새 범위로 다시 확인해요"))
            return
        }
        if (location != null) {
            val fix = toFix(location)
            if (fix != null) {
                val now = SystemClock.elapsedRealtime()
                if (LocationFixPolicy.isUsable(fix, now)) {
                    if (!session.finish(token)) return
                    stopAcquisition(keepProviderWatch = true)
                    clearFix()
                    val message = when {
                        fix.isEmulator -> "에뮬레이터 위치를 사용 중이에요 · 실제 차량 위치가 아니에요"
                        fix.isMock -> "모의 위치를 사용 중이에요 · 실제 차량 위치가 아니에요"
                        precision == LocationPrecision.APPROXIMATE -> "대략적 위치를 사용 중이에요 · 가까운 순서는 오차가 있을 수 있어요"
                        else -> "현재 위치를 확인했어요 · 거리에는 측정 오차가 있을 수 있어요"
                    }
                    publish(LocationState(LocationStatus.READY, fix, precision, message))
                    val validFor = LocationFixPolicy.MAX_AGE_MILLIS - (now - fix.elapsedRealtimeMillis) + 1L
                    expiry = Runnable { refreshValidity() }.also { main.postDelayed(it, validFor.coerceAtLeast(1L)) }
                    return
                }
                if (now < fix.elapsedRealtimeMillis || now - fix.elapsedRealtimeMillis > LocationFixPolicy.MAX_AGE_MILLIS) sawStale = true
                if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters <= 0 || fix.accuracyMeters > LocationFixPolicy.MAX_ACCURACY_METERS) sawLowAccuracy = true
            } else sawInvalid = true
        }
        if (remaining.isEmpty()) {
            stopAcquisition(keepProviderWatch = state.isUsable)
            val status = when {
                !providersAvailable() -> LocationStatus.PROVIDERS_DISABLED
                sawStale -> LocationStatus.STALE
                sawLowAccuracy -> LocationStatus.LOW_ACCURACY
                sawInvalid -> LocationStatus.ERROR
                else -> LocationStatus.UNAVAILABLE
            }
            val message = when (status) {
                LocationStatus.PROVIDERS_DISABLED -> "위치 기능이 꺼졌어요 · 정차 후 설정을 확인해 주세요"
                LocationStatus.STALE -> "오래되었거나 시각을 확인할 수 없는 위치예요 · 다시 요청해 주세요"
                LocationStatus.LOW_ACCURACY -> "위치 오차가 너무 커 가까운 곳을 정렬할 수 없어요"
                else -> "현재 위치를 확인하지 못했어요 · 샘플 위치로 자동 전환하지 않아요"
            }
            publishFailure(LocationState(status, precision = precision, message = message))
        }
    }

    /** Invalidates expired fixes even with an idle UI; also called whenever the Activity resumes. */
    fun refreshValidity() = onMain {
        if (closed || !state.isUsable) return@onMain
        val fix = state.fix ?: return@onMain
        val precision = permissionPrecision(appContext)
        when {
            precision == LocationPrecision.NONE -> permissionDenied(false)
            !LocationFixPolicy.isUsable(fix, SystemClock.elapsedRealtime()) -> {
                clearFix()
                publish(LocationState(LocationStatus.STALE, precision = precision,
                    message = if (acquiring) "이전 위치가 오래되어 새 위치를 기다리고 있어요" else "위치를 확인한 지 2분이 지났어요 · 새 위치가 필요해요",
                    isRefreshing = acquiring))
            }
            precision != state.precision -> {
                clearFix()
                publish(LocationState(LocationStatus.STALE, precision = precision, message = "위치 권한 범위가 바뀌었어요 · 현재 위치를 다시 사용해 주세요"))
            }
        }
    }

    fun cancel() = cancelWith(LocationStatus.CANCELLED, "위치 확인을 멈췄어요 · 필요할 때 다시 사용해 주세요")
    fun cancelForSafety() = cancelWith(LocationStatus.SAFETY_RESTRICTED, "위치 권한과 위치 사용 시작은 정차 후 선택해 주세요")

    private fun cancelWith(status: LocationStatus, message: String) = onMain {
        if (closed) return@onMain
        val wasPending = acquiring || state.status == LocationStatus.REQUESTING_PERMISSION
        stopAcquisition()
        if (state.isUsable) publish(state.copy(isRefreshing = false))
        else if (wasPending) publish(LocationState(status, message = message))
        // A previously accepted, still-fresh fix can remain in memory while driving; expiry stays armed.
    }

    /** Ends the user's location session (for example, switching to sample mode). */
    fun clear() = onMain {
        if (closed) return@onMain
        stopAcquisition()
        clearFix()
        publish(LocationState())
    }

    private fun publishFailure(failure: LocationState) {
        if (failure.status == LocationStatus.PROVIDERS_DISABLED) {
            stopAcquisition()
            clearFix()
            publish(failure)
            return
        }
        val fix = state.fix
        if (fix != null && state.isUsable && state.precision == permissionPrecision(appContext) &&
            LocationFixPolicy.isUsable(fix, SystemClock.elapsedRealtime())) {
            publish(state.copy(isRefreshing = false, refreshIssue = failure.status,
                message = if (fix.isMock || fix.isEmulator) "시험 위치 갱신이 지연돼 이전 시험 위치를 잠시 사용해요" else "위치 갱신이 지연돼 2분 이내의 이전 위치를 잠시 사용해요"))
        } else {
            clearFix()
            publish(failure)
        }
    }

    private fun stopAcquisition(keepProviderWatch: Boolean = false) {
        session.cancel()
        acquiring = false
        timeout?.let(main::removeCallbacks)
        timeout = null
        signals.values.forEach { runCatching { it.cancel() } }
        signals.clear()
        remaining.clear()
        if (receiverRegistered && !keepProviderWatch) {
            runCatching { appContext.unregisterReceiver(providerChanges) }
            receiverRegistered = false
        }
    }

    private fun clearFix() {
        expiry?.let(main::removeCallbacks)
        expiry = null
    }

    private fun providersAvailable(): Boolean = runCatching {
        val current = manager ?: return@runCatching false
        LocationManagerCompat.isLocationEnabled(current) && current.getProviders(true).any { it != LocationManager.PASSIVE_PROVIDER }
    }.getOrDefault(false)

    private fun publish(next: LocationState) {
        if (closed) return
        state = next
        onState(next)
    }

    override fun close() = onMain {
        if (closed) return@onMain
        stopAcquisition()
        clearFix()
        session.close()
        state = LocationState()
        closed = true
        main.removeCallbacksAndMessages(null)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 20_000L

        fun permissionPrecision(context: Context): LocationPrecision = when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationPrecision.PRECISE
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationPrecision.APPROXIMATE
            else -> LocationPrecision.NONE
        }

        internal fun toFix(location: Location): LocationFix? = runCatching {
            require(location.hasAccuracy())
            require(location.elapsedRealtimeNanos > 0)
            LocationFix(GeoPoint(location.latitude, location.longitude), location.accuracy,
                TimeUnit.NANOSECONDS.toMillis(location.elapsedRealtimeNanos), LocationCompat.isMock(location),
                Build.HARDWARE.equals("ranchu", ignoreCase = true) || Build.HARDWARE.equals("goldfish", ignoreCase = true))
        }.getOrNull()
    }
}
