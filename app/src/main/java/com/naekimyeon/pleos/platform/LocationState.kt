package com.naekimyeon.pleos.platform

import com.naekimyeon.pleos.domain.LocationFix

enum class LocationStatus {
    IDLE, REQUESTING_PERMISSION, LOCATING, READY, PERMISSION_DENIED, PERMISSION_BLOCKED,
    PROVIDERS_DISABLED, UNAVAILABLE, TIMED_OUT, STALE, LOW_ACCURACY, CANCELLED, SAFETY_RESTRICTED, ERROR
}

/** Granted permission granularity; actual measurement uncertainty remains fix.accuracyMeters. */
enum class LocationPrecision { NONE, APPROXIMATE, PRECISE }

data class LocationState(
    val status: LocationStatus = LocationStatus.IDLE,
    val fix: LocationFix? = null,
    val precision: LocationPrecision = LocationPrecision.NONE,
    val message: String = "현재 위치를 사용하면 가까운 곳을 찾을 수 있어요",
    val isRefreshing: Boolean = false,
    val refreshIssue: LocationStatus? = null
) {
    val isUsable: Boolean get() = status == LocationStatus.READY && fix != null
    override fun toString() = "LocationState(status=$status, precision=$precision, hasFix=${fix != null}, isMock=${fix?.isMock == true}, isEmulator=${fix?.isEmulator == true})"
}

/** MainActivity applies this before any location permission UI or sensor request. */
internal object LocationInteractionPolicy {
    fun canRequest(foreground: Boolean, safetyKnown: Boolean, requiresDistractionOptimization: Boolean) =
        foreground && safetyKnown && !requiresDistractionOptimization

    fun canEnableRememberedPreference(foreground: Boolean, safetyKnown: Boolean,
        requiresDistractionOptimization: Boolean, hasPermission: Boolean) =
        hasPermission && canRequest(foreground, safetyKnown, requiresDistractionOptimization)
}

/** Fences late results from cancelled, replaced, completed, and destroyed acquisitions. */
internal class LocationSessionGuard {
    private var generation = 0L
    private var active = false
    private var closed = false

    fun begin(): Long? {
        if (closed) return null
        generation++
        active = true
        return generation
    }

    fun isCurrent(token: Long) = !closed && active && token == generation

    fun finish(token: Long): Boolean {
        if (!isCurrent(token)) return false
        cancel()
        return true
    }

    fun cancel() {
        generation++
        active = false
    }

    fun close() {
        cancel()
        closed = true
    }
}
