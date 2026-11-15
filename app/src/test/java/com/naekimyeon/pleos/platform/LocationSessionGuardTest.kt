package com.naekimyeon.pleos.platform

import org.junit.Assert.*
import org.junit.Test

class LocationSessionGuardTest {
    @Test fun cancelledRequestCannotAcceptLateFix() {
        val gate = LocationSessionGuard()
        val token = gate.begin()!!
        gate.cancel()
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.finish(token))
    }

    @Test fun replacementRequestRejectsPreviousProvidersCallback() {
        val gate = LocationSessionGuard()
        val previous = gate.begin()!!
        val current = gate.begin()!!
        assertFalse(gate.finish(previous))
        assertTrue(gate.isCurrent(current))
        assertTrue(gate.finish(current))
    }

    @Test fun firstAcceptedProviderCompletesRequestExactlyOnce() {
        val gate = LocationSessionGuard()
        val token = gate.begin()!!
        assertTrue(gate.finish(token))
        assertFalse(gate.finish(token))
        assertFalse(gate.isCurrent(token))
    }

    @Test fun destroyedAdapterCannotRestartOrAcceptAnyCallback() {
        val gate = LocationSessionGuard()
        val token = gate.begin()!!
        gate.close()
        assertNull(gate.begin())
        assertFalse(gate.finish(token))
    }

    @Test fun timeoutAndNewRequestHaveDifferentGeneration() {
        val gate = LocationSessionGuard()
        val expired = gate.begin()!!
        gate.cancel()
        val retry = gate.begin()!!
        assertNotEquals(expired, retry)
        assertFalse(gate.isCurrent(expired))
        assertTrue(gate.isCurrent(retry))
    }

    @Test fun permissionUiRequiresForegroundKnownUnrestrictedPolicy() {
        assertTrue(LocationInteractionPolicy.canRequest(true, true, false))
        assertFalse(LocationInteractionPolicy.canRequest(true, true, true))
        assertFalse(LocationInteractionPolicy.canRequest(true, false, false))
        assertFalse(LocationInteractionPolicy.canRequest(true, false, true))
        assertFalse(LocationInteractionPolicy.canRequest(false, true, false))
    }

    @Test fun rememberedOptInRequiresGrantedPermissionAndSafeForegroundInteraction() {
        assertTrue(LocationInteractionPolicy.canEnableRememberedPreference(true, true, false, true))
        assertFalse(LocationInteractionPolicy.canEnableRememberedPreference(true, true, false, false))
        assertFalse(LocationInteractionPolicy.canEnableRememberedPreference(true, true, true, true))
        assertFalse(LocationInteractionPolicy.canEnableRememberedPreference(true, false, false, true))
        assertFalse(LocationInteractionPolicy.canEnableRememberedPreference(false, true, false, true))
    }
}
