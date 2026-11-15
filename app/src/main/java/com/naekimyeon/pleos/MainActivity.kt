package com.naekimyeon.pleos

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naekimyeon.pleos.domain.IncomingRequest
import com.naekimyeon.pleos.domain.Need
import com.naekimyeon.pleos.domain.Place
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
import com.naekimyeon.pleos.ui.NaekimyeonApp

/** The Activity owns external connections; UI state stays separate from platform authority. */
class MainActivity : ComponentActivity() {
    private var navigation by mutableStateOf(NavigationState(NavigationStatus.IDLE, "내비게이션 연결 확인 중"))
    private var safety by mutableStateOf(DrivingSafetyState(SafetyCapability.CONNECTING))
    private var incoming by mutableStateOf<IncomingRequest?>(null)
    private var requestSequence = 0L
    private var pendingGleo: Pair<Long, GleoBreakRequest>? = null
    private var pendingDeadline = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gleoTimeout = Runnable {
        failPendingGleo("The sample screen could not be opened in time. Please try again.")
    }
    private var navi: NaviAdapter? = null
    private var safetyAdapter: DrivingSafetyAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestSequence = savedInstanceState?.getLong("requestSequence") ?: 0L
        navi = NaviAdapter(this) { navigation = it }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            try {
                safetyAdapter = DrivingSafetyAdapter(this) {
                    safety = it
                    if (it.capability != SafetyCapability.AVAILABLE || it.restricted) {
                        failPendingGleo("The sample request was cancelled because vehicle interaction is restricted.")
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
                onNavigate = ::navigateAfterConfirmation,
                restricted = safety.capability != SafetyCapability.AVAILABLE || safety.restricted,
                restrictionMessage = safety.message,
                incomingRequest = incoming,
                onIncomingRequestApplied = ::onIncomingApplied,
                onIncomingFeelingUnmatched = ::onIncomingFeelingUnmatched,
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
        navi?.start()
    }

    private fun navigateAfterConfirmation(place: Place) {
        if (safety.capability != SafetyCapability.AVAILABLE || safety.restricted) {
            navigation = NavigationState(NavigationStatus.FAILED, "차량 이용 제한이 해제된 뒤 다시 선택해 주세요")
            return
        }
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
                if (safety.capability != SafetyCapability.AVAILABLE || safety.restricted) {
                    GleoBridge.respond(this, GleoResponseStatus.FAIL, "Detailed sample suggestions are unavailable while vehicle interaction is restricted or unknown.")
                    return
                }
                val request = result.request
                val need = when (request.need) {
                    "breeze" -> Need.BREEZE
                    "conversation" -> Need.CONVERSATION
                    "shelter" -> Need.SHELTER
                    null -> null
                    else -> return
                }
                val id = ++requestSequence
                pendingGleo = id to request
                pendingDeadline = SystemClock.elapsedRealtime() + 6_000L
                mainHandler.postDelayed(gleoTimeout, 6_000L)
                incoming = IncomingRequest(id, need, request.timeMinutes, request.noTimeLimit, request.feeling)
            }
        }
    }

    private fun onIncomingApplied(id: Long) {
        val pending = pendingGleo ?: return
        if (pending.first != id) return
        if (SystemClock.elapsedRealtime() > pendingDeadline ||
            safety.capability != SafetyCapability.AVAILABLE || safety.restricted) {
            failPendingGleo("The sample request expired or vehicle interaction became restricted.")
            return
        }
        mainHandler.removeCallbacks(gleoTimeout)
        pendingGleo = null
        incoming = null
        GleoBridge.respondSampleOpened(this, pending.second)
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
