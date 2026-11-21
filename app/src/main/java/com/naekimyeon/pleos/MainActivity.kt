package com.naekimyeon.pleos

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naekimyeon.pleos.domain.IncomingRequest
import com.naekimyeon.pleos.domain.Need
import com.naekimyeon.pleos.domain.Place
import com.naekimyeon.pleos.domain.LocationFixPolicy
import com.naekimyeon.pleos.domain.NearbyPlaces
import com.naekimyeon.pleos.platform.DrivingSafetyAdapter
import com.naekimyeon.pleos.platform.DrivingSafetyState
import com.naekimyeon.pleos.platform.GleoBridge
import com.naekimyeon.pleos.platform.GleoBreakRequest
import com.naekimyeon.pleos.platform.GleoParseResult
import com.naekimyeon.pleos.platform.GleoResponseStatus
import com.naekimyeon.pleos.platform.NaviAdapter
import com.naekimyeon.pleos.platform.NavigationState
import com.naekimyeon.pleos.platform.NavigationStatus
import com.naekimyeon.pleos.platform.SafetyCapability
import com.naekimyeon.pleos.platform.ForegroundLocationAdapter
import com.naekimyeon.pleos.platform.LocationInteractionPolicy
import com.naekimyeon.pleos.platform.LocationPrecision
import com.naekimyeon.pleos.platform.LocationState
import com.naekimyeon.pleos.platform.LocationStatus
import com.naekimyeon.pleos.platform.LocationPreferenceStore
import com.naekimyeon.pleos.ui.NaekimyeonApp

/** The Activity owns external connections; UI state stays separate from platform authority. */
class MainActivity : ComponentActivity() {
    private var navigation by mutableStateOf(NavigationState(NavigationStatus.IDLE, "내비게이션 연결 확인 중"))
    private var safety by mutableStateOf(DrivingSafetyState(SafetyCapability.CONNECTING))
    private var incoming by mutableStateOf<IncomingRequest?>(null)
    private var requestSequence = 0L
    private var navigationPlaceId by mutableStateOf<String?>(null)
    private var pendingGleo: Pair<Long, GleoBreakRequest>? = null
    private var pendingDeadline = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gleoTimeout = Runnable {
        failPendingGleo("The sample screen could not be opened in time. Please try again.")
    }
    private var navi: NaviAdapter? = null
    private var safetyAdapter: DrivingSafetyAdapter? = null
    private var locationAdapter: ForegroundLocationAdapter? = null
    private var locationState by mutableStateOf(LocationState())
    private var activityResumed = false
    private var locationSessionOptIn = false
    private var locationPermissionInFlight = false
    private var locationPermissionAuthorized = false
    private var locationAfterPermissionPending = false
    private var locationRationale: AlertDialog? = null
    private lateinit var locationPreferences: LocationPreferenceStore
    private var useLocationOnLaunch by mutableStateOf(false)
    private var pendingPreferenceEnable = false
    private var locationSettingsInFlight = false
    private val locationRefresh = Runnable { refreshLocationSession() }
    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationPermissionInFlight = false
        if (!locationPermissionAuthorized) return@registerForActivityResult
        if (ForegroundLocationAdapter.permissionPrecision(this) == LocationPrecision.NONE) {
            locationPermissionAuthorized = false
            pendingPreferenceEnable = false
            val blocked = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            locationAdapter?.permissionDenied(blocked)
        } else {
            locationAfterPermissionPending = true
            completeLocationPermissionFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPreferences = LocationPreferenceStore(this)
        useLocationOnLaunch = locationPreferences.isEnabled()
        locationSessionOptIn = useLocationOnLaunch
        requestSequence = savedInstanceState?.getLong("requestSequence") ?: 0L
        locationAdapter = ForegroundLocationAdapter(this) {
            locationState = it
            if (it.status == LocationStatus.PERMISSION_DENIED || it.status == LocationStatus.PERMISSION_BLOCKED) {
                locationSessionOptIn = false
                pendingPreferenceEnable = false
                updateLocationPreference(false)
                mainHandler.removeCallbacks(locationRefresh)
            }
        }
        navi = NaviAdapter(this) { navigation = it }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            try {
                safetyAdapter = DrivingSafetyAdapter(this) {
                    safety = it
                    if (!it.isKnown || it.requiresDistractionOptimization) {
                        // A permission grant arriving after a transition to D must not start a session.
                        locationPermissionAuthorized = false
                        locationAfterPermissionPending = false
                        pendingPreferenceEnable = false
                        locationRationale?.dismiss()
                        if (!locationSessionOptIn) locationAdapter?.cancelForSafety()
                    }
                    if (it.isKnown) dispatchPendingWhenReady()
                    else if (it.capability != SafetyCapability.CONNECTING) {
                        failPendingGleo("The vehicle interaction state is unavailable. Please try again when the vehicle service is connected.")
                    }
                }.also { it.start() }
            } catch (error: LinkageError) {
                safety = DrivingSafetyState(SafetyCapability.UNAVAILABLE)
            }
        } else {
            // This release is intended for Pleos. No manual switch can bypass vehicle restrictions.
            safety = DrivingSafetyState(SafetyCapability.UNAVAILABLE)
        }
        navi?.start()
        setContent {
            NaekimyeonApp(
                navigationStatus = navigation.message,
                navigationPlaceId = navigationPlaceId,
                onNavigate = ::navigateAfterConfirmation,
                restricted = safety.capability != SafetyCapability.AVAILABLE || safety.restricted,
                restrictionMessage = safety.message,
                incomingRequest = incoming,
                onIncomingRequestApplied = ::onIncomingApplied,
                onIncomingFeelingUnmatched = ::onIncomingFeelingUnmatched,
                drivingAvailable = safety.isKnown,
                drivingMaxStringLength = if (safety.activeRestrictions and 4 != 0)
                    safety.maxRestrictedStringLength.coerceAtLeast(0) else 120,
                drivingMaxCandidates = if (safety.activeRestrictions and 32 != 0) {
                    if (safety.maxContentDepth < 1) 0 else safety.maxCumulativeContentItems.coerceIn(0, 3)
                } else 3,
                onDrivingCandidateApplied = ::onDrivingCandidateApplied,
                locationState = locationState,
                onRequestLocation = ::requestLocationFromUser,
                onSourceChanged = ::onDiscoverySourceChanged,
                onIncomingNearbyApplied = ::onIncomingNearbyApplied,
                onIncomingNearbyUnavailable = ::onIncomingNearbyUnavailable,
                useLocationOnLaunch = useLocationOnLaunch,
                onUseLocationOnLaunchChanged = ::onUseLocationOnLaunchChanged,
            )
        }
        // Launcher recreation does not re-deliver external actions already handled by this process.
        if (savedInstanceState == null) handleGleo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleo(intent)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (locationSettingsInFlight) {
            locationSettingsInFlight = false
            if (pendingPreferenceEnable && canRememberLocationPreference()) {
                updateLocationPreference(true)
                locationSessionOptIn = true
            }
            pendingPreferenceEnable = false
        }
        if (useLocationOnLaunch) locationSessionOptIn = true
        if (!locationSessionOptIn && ForegroundLocationAdapter.permissionPrecision(this) != LocationPrecision.NONE &&
            locationState.status in setOf(LocationStatus.PERMISSION_BLOCKED, LocationStatus.PERMISSION_DENIED)) {
            locationAdapter?.clear() // Permission may have changed in Settings; do not silently opt in.
        }
        locationAdapter?.refreshValidity()
        val startedAfterPermission = completeLocationPermissionFlow()
        if (locationSessionOptIn && !startedAfterPermission) refreshLocationSession()
        navi?.start()
    }

    override fun onPause() {
        activityResumed = false
        mainHandler.removeCallbacks(locationRefresh)
        // The system permission window pauses this Activity. awaitingPermission() already
        // stopped all location acquisition; keep the user's pending choice until the result.
        if (!locationPermissionInFlight) locationAdapter?.cancel()
        locationRationale?.dismiss()
        if (!locationPermissionInFlight) {
            locationPermissionAuthorized = false
            locationAfterPermissionPending = false
            if (!locationSettingsInFlight) pendingPreferenceEnable = false
        }
        super.onPause()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(locationRefresh)
        if (!locationPermissionInFlight) locationAdapter?.cancel()
        super.onStop()
    }

    private fun canOpenLocationPermission() = LocationInteractionPolicy.canRequest(
        activityResumed && !isFinishing && !isDestroyed, safety.isKnown, safety.requiresDistractionOptimization)

    private fun canRememberLocationPreference() = LocationInteractionPolicy.canEnableRememberedPreference(
        activityResumed && !isFinishing && !isDestroyed, safety.isKnown, safety.requiresDistractionOptimization,
        ForegroundLocationAdapter.permissionPrecision(this) != LocationPrecision.NONE)

    private fun requestLocationFromUser() {
        if (!canOpenLocationPermission()) {
            if (!locationSessionOptIn) locationAdapter?.cancelForSafety()
            return
        }
        if (locationPermissionInFlight || locationRationale?.isShowing == true) return
        if (locationState.status == LocationStatus.PROVIDERS_DISABLED) {
            openLocationSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        if (ForegroundLocationAdapter.permissionPrecision(this) != LocationPrecision.NONE) {
            locationSessionOptIn = true
            refreshLocationSession()
            return
        }
        if (locationState.status == LocationStatus.PERMISSION_BLOCKED) {
            openLocationSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationRationale = AlertDialog.Builder(this)
                .setTitle("현재 위치로 가까운 곳 찾기")
                .setMessage("위치는 이 화면을 사용하는 동안 기기 안에서 가까운 장소를 정렬하는 데만 사용해요. 대략적 위치도 허용하며, 좌표를 저장하거나 서버로 보내지 않아요.")
                .setPositiveButton("계속") { _, _ -> launchLocationPermissionRequest() }
                .setNegativeButton("사용 안 함") { _, _ ->
                    pendingPreferenceEnable = false
                    locationAdapter?.permissionDenied(false)
                }
                .setOnDismissListener {
                    locationRationale = null
                    if (!locationPermissionInFlight && !locationSettingsInFlight) pendingPreferenceEnable = false
                }
                .show()
        } else launchLocationPermissionRequest()
    }

    private fun openLocationSettings(intent: Intent) {
        if (!canOpenLocationPermission()) return
        try {
            locationSettingsInFlight = true
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            locationSettingsInFlight = false
            pendingPreferenceEnable = false
            locationAdapter?.clear()
            locationState = LocationState(LocationStatus.UNAVAILABLE,
                message = "이 기기에서 설정 화면을 열 수 없어요 · 차량 설정에서 위치 권한을 확인해 주세요")
        } catch (error: SecurityException) {
            locationSettingsInFlight = false
            pendingPreferenceEnable = false
            locationAdapter?.clear()
            locationState = LocationState(LocationStatus.PERMISSION_BLOCKED,
                message = "설정 화면에 접근할 수 없어요 · 차량 설정에서 직접 확인해 주세요")
        }
    }

    private fun onUseLocationOnLaunchChanged(enabled: Boolean) {
        if (!enabled) {
            onDiscoverySourceChanged(true)
            return
        }
        if (!canOpenLocationPermission()) return
        if (canRememberLocationPreference()) {
            updateLocationPreference(true)
            locationSessionOptIn = true
            refreshLocationSession()
        } else {
            pendingPreferenceEnable = true
            requestLocationFromUser()
        }
    }

    private fun updateLocationPreference(enabled: Boolean) {
        useLocationOnLaunch = enabled
        locationPreferences.setEnabled(enabled)
    }

    private fun launchLocationPermissionRequest() {
        if (!canOpenLocationPermission() || locationPermissionInFlight) return
        locationPermissionAuthorized = true
        locationPermissionInFlight = true
        locationAdapter?.awaitingPermission()
        try {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } catch (error: RuntimeException) {
            locationPermissionInFlight = false
            locationPermissionAuthorized = false
            locationAdapter?.permissionDenied(false)
        }
    }

    private fun completeLocationPermissionFlow(): Boolean {
        if (!locationAfterPermissionPending || !locationPermissionAuthorized || !activityResumed) return false
        locationAfterPermissionPending = false
        locationPermissionAuthorized = false
        if (!canOpenLocationPermission()) {
            pendingPreferenceEnable = false
            locationAdapter?.cancelForSafety()
            return false
        }
        if (ForegroundLocationAdapter.permissionPrecision(this) == LocationPrecision.NONE) {
            pendingPreferenceEnable = false
            locationAdapter?.permissionDenied(false)
            return false
        }
        if (pendingPreferenceEnable && canRememberLocationPreference()) updateLocationPreference(true)
        pendingPreferenceEnable = false
        locationSessionOptIn = true
        refreshLocationSession()
        return true
    }

    private fun refreshLocationSession() {
        mainHandler.removeCallbacks(locationRefresh)
        if (!activityResumed || !locationSessionOptIn) return
        if (ForegroundLocationAdapter.permissionPrecision(this) == LocationPrecision.NONE) {
            locationSessionOptIn = false
            updateLocationPreference(false)
            locationAdapter?.permissionDenied(false)
            return
        }
        locationAdapter?.refreshValidity()
        locationAdapter?.request(keepCurrent = true)
        if (activityResumed && locationSessionOptIn) mainHandler.postDelayed(locationRefresh, 45_000L)
    }

    private fun onDiscoverySourceChanged(isSample: Boolean) {
        if (!isSample) return // Returning to nearby mode alone is not renewed location consent.
        updateLocationPreference(false)
        pendingPreferenceEnable = false
        locationSessionOptIn = false
        locationPermissionAuthorized = false
        locationAfterPermissionPending = false
        mainHandler.removeCallbacks(locationRefresh)
        locationRationale?.dismiss()
        locationAdapter?.clear()
    }

    private fun navigateAfterConfirmation(place: Place) {
        locationAdapter?.refreshValidity()
        if (!place.provenance.isSample && !hasUsableNearbyFix(place)) {
            navigation = NavigationState(NavigationStatus.FAILED, "현재 위치와 가까운 장소를 다시 확인한 뒤 안내해 주세요")
            return
        }
        val contentBlocked = safety.restricted && safety.activeRestrictions and 32 != 0 &&
            (safety.maxCumulativeContentItems < 1 || safety.maxContentDepth < 1)
        val confirmationTooLong = safety.restricted && safety.activeRestrictions and 4 != 0 &&
            safety.maxRestrictedStringLength < "${place.title} 안내".length
        if (!safety.isKnown || contentBlocked || confirmationTooLong) {
            navigation = NavigationState(NavigationStatus.FAILED, "차량 상태를 확인한 뒤 다시 선택해 주세요")
            return
        }
        navigationPlaceId = place.id
        navi?.navigate(place.latitude, place.longitude, place.title)
    }

    private fun handleGleo(intent: Intent?) {
        when (val result = GleoBridge.parse(intent)) {
            GleoParseResult.NotHandled -> Unit
            is GleoParseResult.Asking -> GleoBridge.respond(this, GleoResponseStatus.ASKING, result.message)
            is GleoParseResult.Rejected -> GleoBridge.respond(this, GleoResponseStatus.FAIL, result.message)
            is GleoParseResult.Accepted -> {
                if (pendingGleo != null) {
                    GleoBridge.respond(this, GleoResponseStatus.FAIL, "Another sample request is being opened. Please wait.")
                    return
                }
                if (!safety.isKnown && safety.capability != SafetyCapability.CONNECTING) {
                    GleoBridge.respond(this, GleoResponseStatus.FAIL, "The vehicle interaction state is unavailable.")
                    return
                }
                val request = result.request
                val id = ++requestSequence
                pendingGleo = id to request
                pendingDeadline = SystemClock.elapsedRealtime() + 6_000L
                mainHandler.postDelayed(gleoTimeout, 6_000L)
                dispatchPendingWhenReady()
            }
        }
    }

    private fun dispatchPendingWhenReady() {
        val pending = pendingGleo ?: return
        if (!safety.isKnown || incoming != null) return
        if (SystemClock.elapsedRealtime() > pendingDeadline) {
            failPendingGleo("The request expired before the vehicle state was available.")
            return
        }
        val request = pending.second
        val need = when (request.need) {
            "breeze" -> Need.BREEZE
            "conversation" -> Need.CONVERSATION
            "shelter" -> Need.SHELTER
            else -> null
        }
        incoming = IncomingRequest(pending.first, need, request.timeMinutes, request.noTimeLimit, request.feeling)
    }

    private fun onIncomingApplied(id: Long) {
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        if (SystemClock.elapsedRealtime() > pendingDeadline ||
            !safety.isKnown) {
            failPendingGleo("The sample request expired or vehicle state became unavailable.")
            return
        }
        // A transition to driving must be acknowledged by the actual compact candidate callback.
        if (safety.restricted) return
        mainHandler.removeCallbacks(gleoTimeout)
        pendingGleo = null
        incoming = null
        GleoBridge.respondSampleOpened(this, pending.second)
    }

    private fun onDrivingCandidateApplied(id: Long, place: Place?) {
        if (place != null && !place.provenance.isSample) {
            onIncomingNearbyApplied(id, place)
            return
        }
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        if (!safety.isKnown || SystemClock.elapsedRealtime() > pendingDeadline) {
            failPendingGleo("The request expired or the vehicle state is unavailable.")
            return
        }
        mainHandler.removeCallbacks(gleoTimeout)
        pendingGleo = null
        incoming = null
        if (place == null) {
            GleoBridge.respond(this, GleoResponseStatus.ASKING,
                "No sample destination fits these conditions. Please change the request by voice or when unrestricted.")
        } else {
            GleoBridge.respondDrivingCandidate(this, pending.second, place.title)
        }
    }

    private fun hasUsableNearbyFix(place: Place? = null): Boolean {
        val fix = locationState.fix ?: return false
        if (!locationState.isUsable || !LocationFixPolicy.isUsable(fix, SystemClock.elapsedRealtime())) return false
        if (ForegroundLocationAdapter.permissionPrecision(this) == LocationPrecision.NONE) return false
        return place == null || NearbyPlaces.find(fix.point).any { it.id == place.id }
    }

    private fun onIncomingNearbyApplied(id: Long, place: Place?) {
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        locationAdapter?.refreshValidity()
        if (!safety.isKnown || SystemClock.elapsedRealtime() > pendingDeadline) {
            failPendingGleo("The request expired or vehicle interaction state is unavailable.")
            return
        }
        if (place == null || place.provenance.isSample || !hasUsableNearbyFix(place)) {
            onIncomingNearbyUnavailable(id)
            return
        }
        val fix = locationState.fix ?: return
        mainHandler.removeCallbacks(gleoTimeout)
        pendingGleo = null
        incoming = null
        GleoBridge.respondNearbyCandidate(this, pending.second, place.title, fix.isMock,
            locationState.precision == LocationPrecision.APPROXIMATE, fix.isEmulator)
    }

    private fun onIncomingNearbyUnavailable(id: Long) {
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        pendingGleo = null
        incoming = null
        mainHandler.removeCallbacks(gleoTimeout)
        GleoBridge.respond(this, GleoResponseStatus.ASKING,
            "A fresh location and a place within the supported catalog area are needed. Enable location when interaction is unrestricted, or explicitly choose sample mode.")
    }

    private fun onIncomingFeelingUnmatched(id: Long) {
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        pendingGleo = null
        incoming = null
        mainHandler.removeCallbacks(gleoTimeout)
        GleoBridge.respond(this, GleoResponseStatus.ASKING,
            "Please choose a sample mood: reset, together, discovery, or rest. The app does not infer feelings it cannot match.")
    }

    private fun failPendingGleo(message: String) {
        if (pendingGleo == null) return
        pendingGleo = null
        incoming = null
        mainHandler.removeCallbacks(gleoTimeout)
        GleoBridge.respond(this, GleoResponseStatus.FAIL, message)
    }

    override fun onDestroy() {
        locationSessionOptIn = false
        locationPermissionAuthorized = false
        locationAfterPermissionPending = false
        locationRationale?.dismiss()
        locationAdapter?.close()
        locationAdapter = null
        failPendingGleo("The sample screen was closed before the request completed.")
        mainHandler.removeCallbacksAndMessages(null)
        safetyAdapter?.close()
        navi?.close()
        safetyAdapter = null
        navi = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("requestSequence", requestSequence)
        super.onSaveInstanceState(outState)
    }
}
