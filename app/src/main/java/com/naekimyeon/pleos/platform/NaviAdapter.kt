package com.naekimyeon.pleos.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus as PleosNavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

enum class NavigationStatus {
    IDLE, CONNECTING, READY, REQUESTING, CONFIRMED, UNAVAILABLE, PERMISSION_DENIED, FAILED, CLOSED
}

data class NavigationState(
    val status: NavigationStatus,
    val message: String
)

/**
 * Real Pleos NaviHelper 3.0.5 bridge. All public calls and callbacks are serialized on main.
 * Only an explicit navigate() call mutates the route. A sent request is never reported as success.
 * The caller owns user confirmation and must call close() when its Activity is destroyed.
 */
class NaviAdapter(context: Context, private val onState: (NavigationState) -> Unit) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var connected = false
    private var closed = false
    private var generation = 0
    private var pending: PendingDestination? = null
    private var timeout: Runnable? = null
    private var connectionTimeout: Runnable? = null
    private var verificationPoll: Runnable? = null

    private data class PendingDestination(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val confirmation: RouteConfirmationGate = RouteConfirmationGate(latitude, longitude)
    )

    fun start() = onMain {
        if (closed || helper != null) return@onMain
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            publish(NavigationStatus.UNAVAILABLE, "Pleos 차량 환경에서 내비게이션을 연결할 수 있어요")
            return@onMain
        }
        if (!hasPermission(PERMISSION_ROUTE) || !hasPermission(PERMISSION_SEARCH)) {
            publish(NavigationStatus.PERMISSION_DENIED, "내비게이션 권한을 사용할 수 없어요")
            return@onMain
        }
        val token = ++generation
        publish(NavigationStatus.CONNECTING, "Pleos 내비게이션 연결 중")
        connectionTimeout = Runnable { connectionFailed(token, false) }.also {
            main.postDelayed(it, CONNECTION_TIMEOUT_MS)
        }
        try {
            val next = NaviHelper(appContext)
            helper = next
            val events = createListener(token)
            listener = events
            next.initialize(
                onConnected = {
                    onMain {
                        if (closed || token != generation) return@onMain
                        try {
                            next.addListener(events)
                            clearConnectionTimeout()
                            connected = true
                            trace("connected")
                            publish(NavigationStatus.READY, "Pleos 내비게이션 연결됨")
                        } catch (error: SecurityException) {
                            connectionFailed(token, true)
                        } catch (error: Exception) {
                            connectionFailed(token, false)
                        }
                    }
                },
                onFailed = { error ->
                    onMain { connectionFailed(token, error is SecurityException) }
                }
            )
        } catch (error: SecurityException) {
            connectionFailed(token, true)
        } catch (error: Exception) {
            connectionFailed(token, false)
        } catch (error: LinkageError) {
            connectionFailed(token, false)
        }
    }

    /** Explicit destination handoff. Invoke only after the user chooses the navigation action. */
    fun navigate(latitude: Double, longitude: Double, name: String) = onMain {
        if (closed) return@onMain
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 || name.isBlank() || name.length > 200
        ) {
            publish(NavigationStatus.FAILED, "목적지 정보를 확인해 주세요")
            return@onMain
        }
        if (pending != null) {
            publish(NavigationStatus.REQUESTING, "이전 경로 요청을 확인하고 있어요")
            return@onMain
        }
        val current = helper
        if (!connected || current == null) {
            publish(NavigationStatus.UNAVAILABLE, "내비게이션이 연결되지 않았어요 · 잠시 후 다시 시도해 주세요")
            return@onMain
        }
        val destination = PendingDestination(latitude, longitude, name.trim())
        pending = destination
        publish(NavigationStatus.REQUESTING, "${destination.name} 경로 요청 중 · 내비 응답을 기다려요")
        val deadline = Runnable {
            if (!closed && pending === destination) {
                pending = null
                timeout = null
                verificationPoll?.let(main::removeCallbacks)
                verificationPoll = null
                trace("routeTimeout")
                publish(NavigationStatus.FAILED, "내비 응답을 확인하지 못했어요 · 내비에서 경로를 확인해 주세요")
            }
        }
        timeout = deadline
        main.postDelayed(deadline, ROUTE_TIMEOUT_MS)
        try {
            current.requestRoute(
                RouteInfo(
                    latitude = latitude,
                    longitude = longitude,
                    poiName = destination.name,
                    poiId = "",
                    address = "",
                    poiSubId = ""
                )
            )
            trace("requestRouteSubmitted")
            if (pending === destination) startVerification(destination)
        } catch (error: SecurityException) {
            failPending(NavigationStatus.PERMISSION_DENIED, "내비게이션 경로 변경 권한을 사용할 수 없어요")
        } catch (error: Exception) {
            failPending(NavigationStatus.FAILED, "경로 요청을 전달하지 못했어요")
        }
    }

    private fun createListener(token: Int) = object : NaviHelperEventListener {
        override fun onRoutingCompleted() = routingCompleted(token, "onRoutingCompleted")

        // The official SDK dispatches this older callback separately; some providers still emit it.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onRouteEnded() = routingCompleted(token, "onRouteEnded")

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = receive(token) {
            trace("onRouteStarted")
            pending?.confirmation?.onRouteStarted()
            requestRouteSnapshot()
        }

        override fun onNavigationStatus(status: PleosNavigationStatus) = receive(token) {
            val active = status == PleosNavigationStatus.ROUTE_DRIVE
            trace("onNavigationStatus active=$active")
            val destination = pending ?: return@receive
            destination.confirmation.onNavigationActive(active)
            confirmIfReady()
            if (active) requestRouteSnapshot()
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = receive(token) {
            // On the tested 2.1.0 provider this is false even after ROUTE_DRIVE; do not use it
            // to establish or revoke guidance confirmation.
            trace("onRouteStateInfo isRouting=${routeStateInfo.isRouting}")
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = receive(token) {
            val destination = pending ?: return@receive
            destination.confirmation.onDestination(destinationInfo.latitude, destinationInfo.longitude)
            trace("onDestinationInfo freshMatch=${destination.confirmation.hasMatchingDestination}")
            confirmIfReady()
        }

        override fun onDestinationChanged() = receive(token) {
            trace("onDestinationChanged")
            requestRouteSnapshot()
        }

        override fun onError(errorCode: NaviErrorCode) = receive(token) {
            trace("onError")
            if (pending != null) failPending(NavigationStatus.FAILED, "내비게이션이 경로 요청을 처리하지 못했어요")
        }

        override fun onRouteCancelled() = receive(token) {
            trace("onRouteCancelled")
            val destination = pending ?: return@receive
            destination.confirmation.onUncorrelatedCancellation()
            publish(NavigationStatus.REQUESTING, "내비게이션 경로 변경을 확인 중이에요")
        }
    }

    private fun routingCompleted(token: Int, event: String) = receive(token) {
        trace(event)
        val destination = pending ?: return@receive
        destination.confirmation.onRoutingCompleted()
        confirmIfReady()
        requestRouteSnapshot()
    }

    private fun startVerification(destination: PendingDestination) {
        val poll = object : Runnable {
            override fun run() {
                if (closed || pending !== destination) return
                requestRouteSnapshot()
                if (pending === destination) main.postDelayed(this, 2_000L)
            }
        }
        verificationPoll = poll
        main.postDelayed(poll, 1_000L)
    }

    /** Bounded read-only verification, also covering providers that omit completion events. */
    private fun requestRouteSnapshot() {
        if (pending == null || !connected) return
        try {
            helper?.getRouteStateInfo()
            helper?.getDestinationInfo()
            trace("routeSnapshotRequested")
        } catch (error: SecurityException) {
            failPending(NavigationStatus.PERMISSION_DENIED, "목적지 확인 권한을 사용할 수 없어요")
        } catch (error: Exception) {
            failPending(NavigationStatus.FAILED, "경로는 요청했지만 목적지를 확인하지 못했어요")
        }
    }

    private fun confirmIfReady() {
        val destination = pending ?: return
        if (!destination.confirmation.isConfirmed) return
        trace("routeConfirmed")
        clearPending()
        publish(NavigationStatus.CONFIRMED, "${destination.name} 경로 설정을 확인했어요")
    }

    private fun connectionFailed(token: Int, permissionDenied: Boolean) {
        if (closed || generation != token) return
        generation++
        trace("connectionFailed permissionDenied=$permissionDenied")
        releaseHelper()
        publish(
            if (permissionDenied) NavigationStatus.PERMISSION_DENIED else NavigationStatus.UNAVAILABLE,
            if (permissionDenied) "내비게이션 연결 권한을 사용할 수 없어요" else "Pleos 내비게이션 서비스를 사용할 수 없어요"
        )
    }

    private fun failPending(status: NavigationStatus, message: String) {
        clearPending()
        publish(status, message)
    }

    private fun clearPending() {
        verificationPoll?.let(main::removeCallbacks)
        verificationPoll = null
        timeout?.let(main::removeCallbacks)
        timeout = null
        pending = null
    }

    private fun releaseHelper() {
        clearConnectionTimeout()
        clearPending()
        connected = false
        val current = helper
        val events = listener
        helper = null
        listener = null
        if (current != null) {
            if (events != null) runCatching { current.removeListener(events) }
            runCatching { current.release() }
        }
    }

    private fun clearConnectionTimeout() {
        connectionTimeout?.let(main::removeCallbacks)
        connectionTimeout = null
    }

    override fun close() = onMain {
        if (closed) return@onMain
        closed = true
        generation++
        releaseHelper()
        main.removeCallbacksAndMessages(null)
    }

    private fun receive(token: Int, block: () -> Unit) = onMain {
        if (!closed && token == generation) block()
    }

    private fun publish(status: NavigationStatus, message: String) {
        if (!closed) onState(NavigationState(status, message))
    }

    private fun hasPermission(permission: String) =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Diagnostic events contain no coordinates, place names, route details, or account data. */
    private fun trace(event: String) {
        Log.d("NaekimyeonNavi", event)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val PERMISSION_ROUTE = "pleos.car.permission.NAVI_ROUTE"
        const val PERMISSION_SEARCH = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        private const val ROUTE_TIMEOUT_MS = 30_000L
    }
}
