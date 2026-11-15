package com.naekimyeon.pleos.domain

import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private val engine = RecommendationEngine()
    private fun ready(request: RecommendationRequest, places: List<Place> = DemoPlaces.places) =
        (engine.recommend(places, request) as RecommendationResult.Ready).recommendations

    @Test fun unknownTimeDoesNotBecomeADeadline() {
        val items = ready(RecommendationRequest())
        assertEquals(3, items.size)
        assertTrue(items.all { it.budgetStatus == BudgetStatus.NOT_SET && it.stayMinutes == null })
    }

    @Test fun explicitNoDeadlineRemainsDistinctFromUnknown() {
        val items = ready(RecommendationRequest(time = TimePreference.Unlimited))
        assertTrue(items.all { it.budgetStatus == BudgetStatus.NO_DEADLINE && it.stayMinutes == null })
    }

    @Test fun budgetDoesNotAssumeReturnToOrigin() {
        val items = ready(RecommendationRequest(time = TimePreference.Bounded(1)))
        assertEquals(3, items.size)
        assertTrue(items.all { it.budgetStatus == BudgetStatus.RETURN_PLAN_NEEDED && it.stayMinutes == null })
    }

    @Test fun boundedStaySubtractsBothDirectionsAccessAndSafety() {
        val river = ready(RecommendationRequest(time = TimePreference.Bounded(45), returnPlan = ReturnPlan.SAME_ORIGIN))
            .first { it.place.id == "riverside" }
        assertEquals(35, river.overheadMinutes)
        assertEquals(10, river.stayMinutes)
    }

    @Test fun shortBudgetReturnsEmptyInsteadOfAnImpossibleTrip() {
        assertEquals(
            RecommendationResult.Empty(EmptyReason.NOT_ENOUGH_TIME),
            engine.recommend(DemoPlaces.places, RecommendationRequest(time = TimePreference.Bounded(30), returnPlan = ReturnPlan.SAME_ORIGIN))
        )
    }

    @Test fun unknownTravelTimeCannotBePresentedAsFittingBudget() {
        val unknown = DemoPlaces.places.first().copy(returnMinutes = null)
        assertEquals(
            RecommendationResult.Empty(EmptyReason.MISSING_TRAVEL_ESTIMATES),
            engine.recommend(listOf(unknown), RecommendationRequest(time = TimePreference.Bounded(75), returnPlan = ReturnPlan.SAME_ORIGIN))
        )
    }

    @Test fun shelterOnlyIncludesIndoorFixtures() {
        val items = ready(RecommendationRequest(needs = setOf(Need.SHELTER)))
        assertTrue(items.all { it.place.indoor })
        assertEquals("window_cafe", items.single().place.id)
    }

    @Test fun knownParkingFacilityNeverPromisesVacancy() {
        val items = ready(RecommendationRequest())
        assertTrue(items.all { it.place.parking.availability == AvailabilityState.UNKNOWN })
        assertTrue(items.all { it.place.parking.provenance.isSample })
    }

    @Test fun comparisonContainsAtMostThreeUniquePlaces() {
        val many = (1..8).map { DemoPlaces.places.first().copy(id = "place_$it") }
        assertEquals(3, ready(RecommendationRequest(), many + many).size)
    }

    @Test fun conflictingPreferencesHaveAnHonestEmptyState() {
        assertEquals(
            RecommendationResult.Empty(EmptyReason.NO_MATCHING_NEED),
            engine.recommend(DemoPlaces.places, RecommendationRequest(needs = setOf(Need.BREEZE, Need.SHELTER)))
        )
    }

    @Test fun veryLargeTravelEstimatesDoNotOverflowIntoAFittingTrip() {
        val huge = DemoPlaces.places.first().copy(outboundMinutes = Int.MAX_VALUE, returnMinutes = Int.MAX_VALUE)
        assertEquals(RecommendationResult.Empty(EmptyReason.NOT_ENOUGH_TIME), engine.recommend(
            listOf(huge), RecommendationRequest(time = TimePreference.Bounded(45), returnPlan = ReturnPlan.SAME_ORIGIN)
        ))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidBudgetIsRejected() {
        TimePreference.Bounded(241)
    }
}
