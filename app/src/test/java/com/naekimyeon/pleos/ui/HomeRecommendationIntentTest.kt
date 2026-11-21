package com.naekimyeon.pleos.ui

import com.naekimyeon.pleos.domain.DemoPlaces
import com.naekimyeon.pleos.domain.MoodIntent
import com.naekimyeon.pleos.domain.RecommendationRequest
import org.junit.Assert.*
import org.junit.Test

class HomeRecommendationIntentTest {
    private val intent = HomeRecommendationIntent(1, HomeAction.ONE, false, 8, RecommendationRequest(), MoodIntent.REST,
        1_000, 20, DemoPlaces.places, true, null, null)

    @Test fun onlyTheExplicitUnchangedIntentCanResume() {
        assertTrue(intent.isCurrent(8, false, false, 2_000, 20, DemoPlaces.places))
        assertFalse(intent.isCurrent(9, false, false, 2_000, 20, DemoPlaces.places))
        assertFalse(intent.isCurrent(8, true, false, 2_000, 20, DemoPlaces.places))
        assertFalse(intent.isCurrent(8, false, true, 2_000, 20, DemoPlaces.places))
    }

    @Test fun dateTimeoutAndCatalogChangeCannotReopenAnOldRecommendation() {
        assertFalse(intent.isCurrent(8, false, false, 2_000, 21, DemoPlaces.places))
        assertFalse(intent.isCurrent(8, false, false, 62_000, 20, DemoPlaces.places))
        assertFalse(intent.isCurrent(8, false, false, 2_000, 20, DemoPlaces.places.dropLast(1)))
    }
}
