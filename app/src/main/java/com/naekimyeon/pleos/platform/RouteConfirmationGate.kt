package com.naekimyeon.pleos.platform

import kotlin.math.abs

/**
 * A route request is confirmed only with new-route progress and a subsequent matching destination.
 * This does not correlate requests; NaviAdapter permits only one request at a time.
 */
internal class RouteConfirmationGate(
    private val targetLatitude: Double,
    private val targetLongitude: Double
) {
    private var newRouteObserved = false
    private var routingCompleted = false
    private var navigationActive = false
    private var destinationMatched = false

    val isConfirmed: Boolean get() = newRouteObserved && destinationMatched && (routingCompleted || navigationActive)
    val hasMatchingDestination: Boolean get() = destinationMatched

    fun onRouteStarted() {
        // Readbacks received before this event may describe the route being replaced.
        newRouteObserved = true
        routingCompleted = false
        navigationActive = false
        destinationMatched = false
    }

    fun onRoutingCompleted() {
        if (!newRouteObserved) destinationMatched = false
        newRouteObserved = true
        routingCompleted = true
    }

    /** NavigationStatus.ROUTE_DRIVE, not RouteStateInfo.isRouting (which varies by provider). */
    fun onNavigationActive(active: Boolean) {
        navigationActive = newRouteObserved && active
    }

    /** Cancellation may belong to the old route; the pending request itself remains outstanding. */
    fun onUncorrelatedCancellation() {
        newRouteObserved = false
        routingCompleted = false
        navigationActive = false
        destinationMatched = false
    }

    fun onDestination(latitude: Double, longitude: Double) {
        destinationMatched = newRouteObserved &&
            abs(latitude - targetLatitude) <= COORDINATE_TOLERANCE &&
                abs(longitude - targetLongitude) <= COORDINATE_TOLERANCE
    }

    companion object {
        // The navigation provider may adjust the point to a nearby vehicle entrance.
        private const val COORDINATE_TOLERANCE = 0.001
    }
}
