package com.naekimyeon.pleos.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteConfirmationGateTest {
    private fun gate() = RouteConfirmationGate(37.51, 127.01)

    @Test fun requestAloneNeverConfirms() {
        assertFalse(gate().isConfirmed)
    }

    @Test fun routeCompletionWithoutMatchingDestinationNeverConfirms() {
        val state = gate()
        state.onRoutingCompleted()
        assertFalse(state.isConfirmed)
        state.onDestination(35.18, 129.07)
        assertFalse(state.isConfirmed)
    }

    @Test fun matchingDestinationWithoutRouteCompletionNeverConfirms() {
        val state = gate()
        state.onRouteStarted()
        state.onDestination(37.51, 127.01)
        assertFalse(state.isConfirmed)
    }

    @Test fun acceptsBothCallbackOrders() {
        val destinationFirst = gate()
        destinationFirst.onRouteStarted()
        destinationFirst.onDestination(37.51, 127.01)
        destinationFirst.onRoutingCompleted()
        assertTrue(destinationFirst.isConfirmed)

        val completionFirst = gate()
        completionFirst.onRoutingCompleted()
        completionFirst.onDestination(37.51, 127.01)
        assertTrue(completionFirst.isConfirmed)
    }

    @Test fun newerDifferentDestinationInvalidatesEarlierMatch() {
        val state = gate()
        state.onRouteStarted()
        state.onDestination(37.51, 127.01)
        state.onDestination(37.53, 127.01)
        state.onRoutingCompleted()
        assertFalse(state.isConfirmed)
    }

    @Test fun allowsSmallEntranceAdjustmentButRejectsDistantOrInvalidCoordinates() {
        val state = gate()
        state.onRoutingCompleted()
        state.onDestination(37.5105, 127.0105)
        assertTrue(state.isConfirmed)
        state.onDestination(37.512, 127.01)
        assertFalse(state.isConfirmed)
        state.onDestination(Double.NaN, 127.01)
        assertFalse(state.isConfirmed)
        state.onDestination(37.51, Double.POSITIVE_INFINITY)
        assertFalse(state.isConfirmed)
    }

    @Test fun separateRequestStartsUnconfirmedAfterPriorRequestCompletion() {
        val previous = gate()
        previous.onRoutingCompleted()
        previous.onDestination(37.51, 127.01)
        assertTrue(previous.isConfirmed)
        assertFalse(gate().isConfirmed)
    }

    @Test fun activeNavigationAfterRouteStartConfirmsOnlyWithFreshMatchingDestination() {
        val state = gate()
        state.onRouteStarted()
        state.onNavigationActive(true)
        assertFalse(state.isConfirmed)
        state.onDestination(37.51, 127.01)
        assertTrue(state.isConfirmed)
    }

    @Test fun inactiveNavigationDoesNotEraseCompletedRouteEvidence() {
        val state = gate()
        state.onRoutingCompleted()
        state.onNavigationActive(false)
        state.onDestination(37.51, 127.01)
        assertTrue(state.isConfirmed)
    }

    @Test fun activeNavigationWorksWhenDestinationCallbackArrivesAfterStartButBeforeStatus() {
        val state = gate()
        state.onRouteStarted()
        state.onDestination(37.51, 127.01)
        state.onNavigationActive(false)
        assertFalse(state.isConfirmed)
        state.onNavigationActive(true)
        assertTrue(state.isConfirmed)
    }

    @Test fun existingNearbyActiveRouteDoesNotConfirmNewRequest() {
        val state = gate()
        state.onNavigationActive(true)
        state.onDestination(37.5105, 127.0105)
        assertFalse(state.isConfirmed)
        state.onRouteStarted()
        state.onDestination(37.51, 127.01)
        // The old active status must not carry across the new route-start event.
        assertFalse(state.isConfirmed)
    }

    @Test fun completionRequiresDestinationObservedAfterNewRouteProgress() {
        val state = gate()
        state.onDestination(37.51, 127.01)
        state.onRoutingCompleted()
        assertFalse(state.isConfirmed)
        state.onDestination(37.51, 127.01)
        assertTrue(state.isConfirmed)
    }

    @Test fun oldRouteCancellationDoesNotPreventLaterReplacementConfirmation() {
        val state = gate()
        state.onNavigationActive(true)
        state.onDestination(37.51, 127.01)
        state.onUncorrelatedCancellation()
        state.onNavigationActive(false)
        assertFalse(state.isConfirmed)
        // Actual Pleos 2.1.0 ordering: cancellation, then new route started/ended/active.
        state.onRouteStarted()
        state.onRoutingCompleted()
        state.onNavigationActive(true)
        state.onDestination(37.51, 127.01)
        assertTrue(state.isConfirmed)
    }

    @Test fun cancellationDiscardsEarlierEvidenceUntilNewProgressArrives() {
        val state = gate()
        state.onRouteStarted()
        state.onNavigationActive(true)
        state.onUncorrelatedCancellation()
        state.onDestination(37.51, 127.01)
        state.onNavigationActive(true)
        assertFalse(state.isConfirmed)
        state.onRoutingCompleted()
        assertFalse(state.isConfirmed)
        state.onDestination(37.51, 127.01)
        assertTrue(state.isConfirmed)
    }
}
