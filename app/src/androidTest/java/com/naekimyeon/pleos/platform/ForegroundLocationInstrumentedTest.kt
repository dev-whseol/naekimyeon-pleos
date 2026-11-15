package com.naekimyeon.pleos.platform

import android.location.Location
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.naekimyeon.pleos.domain.LocationFixPolicy
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Location conversion; no providers, app-ops, permissions or physical sensors changed. */
@RunWith(AndroidJUnit4::class)
class ForegroundLocationInstrumentedTest {
    private fun location() = Location("test-fixture").apply {
        latitude = 37.5445
        longitude = 127.0374
        accuracy = 25f
        elapsedRealtimeNanos = 500_000_000_000L
        time = 1L // Deliberately unrelated wall clock; freshness must never use this value.
    }

    @Test fun conversionPreservesMonotonicTimestampAndAccuracy() {
        val fix = ForegroundLocationAdapter.toFix(location())!!
        assertEquals(500_000L, fix.elapsedRealtimeMillis)
        assertEquals(25f, fix.accuracyMeters, 0f)
        assertTrue(LocationFixPolicy.isUsable(fix, 620_000L))
        assertFalse(LocationFixPolicy.isUsable(fix, 620_001L))
    }

    @Test fun futureMonotonicTimeCannotBeAcceptedEvenWithValidWallClock() {
        val fix = ForegroundLocationAdapter.toFix(location())!!
        assertFalse(LocationFixPolicy.isUsable(fix, 499_999L))
    }

    @Test fun missingAccuracyIsNeverAssumedPrecise() {
        val missing = location().apply { removeAccuracy() }
        assertNull(ForegroundLocationAdapter.toFix(missing))
    }

    @Test fun missingMonotonicTimestampIsRejected() {
        assertNull(ForegroundLocationAdapter.toFix(location().apply { elapsedRealtimeNanos = 0L }))
    }

    @Test fun invalidCoordinateCannotEnterDomain() {
        assertNull(ForegroundLocationAdapter.toFix(location().apply { latitude = Double.NaN }))
    }

    @Test fun mockProvenanceSurvivesConversionAndStateDoesNotPrintCoordinates() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val platformLocation = location().apply { isMock = true }
        val fix = ForegroundLocationAdapter.toFix(platformLocation)!!
        assertTrue(fix.isMock)
        val state = LocationState(LocationStatus.READY, fix, LocationPrecision.APPROXIMATE)
        assertTrue(state.isUsable)
        assertFalse(state.toString().contains("37.5445"))
        assertFalse(state.toString().contains("127.0374"))
    }

    @Test fun inaccurateFixIsRejectedWithoutCoercingItsAccuracy() {
        val fix = ForegroundLocationAdapter.toFix(location().apply { accuracy = 5_001f })!!
        assertEquals(5_001f, fix.accuracyMeters, 0f)
        assertFalse(LocationFixPolicy.isUsable(fix, 500_000L))
    }

    @Test fun emulatorProvenanceDoesNotAlterFrameworkMockFlag() {
        val fix = ForegroundLocationAdapter.toFix(location())!!
        assertFalse(fix.isMock)
        val emulatedHardware = Build.HARDWARE.equals("ranchu", true) || Build.HARDWARE.equals("goldfish", true)
        assertEquals(emulatedHardware, fix.isEmulator)
    }
}
