package com.naekimyeon.pleos.domain

import org.junit.Assert.*
import org.junit.Test

class CuratedExperienceTest {
    private val engine = SceneRecommender()
    private fun scene(place: Place, time: TimePreference, mood: MoodIntent?) =
        (engine.recommend(listOf(place), RecommendationRequest(time = time), mood) as SceneResult.Ready).scenes.single()

    @Test fun fiveVenuesIncludeTwoIndependentIndoorDestinations() {
        assertEquals(5, CuratedPlaces.places.size)
        assertEquals(setOf("seoul_history_museum", "gwacheon_science_museum"),
            CuratedPlaces.places.filter { it.indoor }.map { it.id }.toSet())
        val indoor = engine.recommend(CuratedPlaces.places, RecommendationRequest(needs = setOf(Need.SHELTER)),
            MoodIntent.REST, limit = Int.MAX_VALUE) as SceneResult.Ready
        assertEquals(2, indoor.scenes.size)
        assertTrue(indoor.scenes.all { it.place.indoor && !it.place.provenance.isSample })
    }

    @Test fun shelterCannotBeSatisfiedByAnOutdoorVenueWithAnAccidentalTag() {
        val incorrectlyTagged = CuratedPlaces.places.first().copy(needs = setOf(Need.SHELTER), indoor = false)
        assertEquals(SceneResult.Empty(EmptyReason.NO_MATCHING_NEED),
            engine.recommend(listOf(incorrectlyTagged), RecommendationRequest(needs = setOf(Need.SHELTER)), null))
    }

    @Test fun eachVenueHasAtLeastTwoOperatorDocumentedAreasAndAllFourActivities() {
        CuratedPlaces.places.forEach { place ->
            val experience = requireNotNull(place.experience)
            assertTrue(experience.points.size >= 2)
            assertEquals(experience.points.size, experience.points.map { it.id }.toSet().size)
            assertTrue(experience.points.all { it.sourceUrl.startsWith("https://") && it.checkedOn == "2026-11-21" })
            assertEquals(MoodIntent.entries.toSet(), experience.byMood.keys)
        }
    }

    @Test fun moodChangesConcreteActivitiesAtEveryRealVenue() {
        CuratedPlaces.places.forEach { place ->
            val results = MoodIntent.entries.map { scene(place, TimePreference.Bounded(45), it) }
            assertEquals(place.id, 4, results.map { it.title }.toSet().size)
            assertEquals(place.id, 4, results.map { it.activity }.toSet().size)
        }
    }

    @Test fun sameMoodDoesNotReuseAGenericGardenActivityAcrossRealVenues() {
        MoodIntent.entries.forEach { mood ->
            val activities = CuratedPlaces.places.map { scene(it, TimePreference.Bounded(45), mood).activity }
            assertEquals(mood.name, CuratedPlaces.places.size, activities.toSet().size)
        }
    }

    @Test fun thirtyAndFortyFiveStayAtOneAreaButChangeTheActivityScope() {
        CuratedPlaces.places.forEach { place ->
            MoodIntent.entries.forEach { mood ->
                val brief = scene(place, TimePreference.Bounded(30), mood)
                val longer = scene(place, TimePreference.Bounded(45), mood)
                assertEquals(1, brief.plannedStops.size)
                assertEquals(brief.plannedStops, longer.plannedStops)
                assertEquals(VisitScope.BRIEF, brief.visitScope)
                assertEquals(VisitScope.SINGLE_POINT, longer.visitScope)
                assertNotEquals("${place.id}/${mood.name}", brief.activity, longer.activity)
                assertEquals(BudgetStatus.TRAVEL_ESTIMATE_NEEDED, brief.recommendation.budgetStatus)
                assertNull(brief.recommendation.stayMinutes)
                assertNull(longer.recommendation.overheadMinutes)
            }
        }
    }

    @Test fun onlyExplicitUnlimitedCanLinkTwoVerifiedAreas() {
        CuratedPlaces.places.forEach { place ->
            val unlimited = scene(place, TimePreference.Unlimited, MoodIntent.DISCOVERY)
            val unspecified = scene(place, TimePreference.Unspecified, MoodIntent.DISCOVERY)
            val bounded = scene(place, TimePreference.Bounded(120), MoodIntent.DISCOVERY)
            assertEquals(2, unlimited.plannedStops.size)
            assertEquals(VisitScope.OPTIONAL_PAIR, unlimited.visitScope)
            assertEquals(BudgetStatus.NO_DEADLINE, unlimited.recommendation.budgetStatus)
            assertEquals(1, unspecified.plannedStops.size)
            assertEquals(VisitScope.OPEN_ENDED, unspecified.visitScope)
            assertEquals(BudgetStatus.NOT_SET, unspecified.recommendation.budgetStatus)
            assertEquals(1, bounded.plannedStops.size)
        }
    }

    @Test fun noDeadlineDoesNotForceARestingUserIntoASecondStop() {
        CuratedPlaces.places.forEach { place ->
            val rest = scene(place, TimePreference.Unlimited, MoodIntent.REST)
            assertEquals(1, rest.plannedStops.size)
            assertEquals(BudgetStatus.NO_DEADLINE, rest.recommendation.budgetStatus)
        }
    }

    @Test fun completeEligiblePoolIsAvailableWithoutChangingDefaultComparisonSize() {
        val all = engine.recommend(CuratedPlaces.places, RecommendationRequest(), null, limit = Int.MAX_VALUE) as SceneResult.Ready
        val comparison = engine.recommend(CuratedPlaces.places, RecommendationRequest(), null) as SceneResult.Ready
        assertEquals(5, all.scenes.size)
        assertEquals(3, comparison.scenes.size)
        assertEquals(5, all.scenes.map { it.place.id }.distinct().size)
        assertEquals(all.scenes.take(3), comparison.scenes)
    }

    @Test fun wholePoolAndVariationNeverBypassIndoorFilterOrInventTravelMinutes() {
        val request = RecommendationRequest(setOf(Need.SHELTER), TimePreference.Bounded(30), ReturnPlan.SAME_ORIGIN)
        val result = engine.recommend(CuratedPlaces.places + CuratedPlaces.places, request,
            MoodIntent.DISCOVERY, variation = Int.MAX_VALUE, limit = Int.MAX_VALUE) as SceneResult.Ready
        assertEquals(2, result.scenes.size)
        assertTrue(result.scenes.all { it.place.indoor && it.plannedStops.size == 1 &&
            it.recommendation.stayMinutes == null && it.recommendation.overheadMinutes == null &&
            it.recommendation.budgetStatus == BudgetStatus.TRAVEL_ESTIMATE_NEEDED })
    }

    @Test fun experienceAreasDoNotChangeTheNavigationRepresentativePoint() {
        val place = CuratedPlaces.places.first { it.id == "gwacheon_science_museum" }
        val result = scene(place, TimePreference.Unlimited, MoodIntent.DISCOVERY)
        assertEquals(37.438119, result.place.latitude, 0.0)
        assertEquals(127.005777, result.place.longitude, 0.0)
        assertEquals(place.id, result.place.id)
        assertFalse(result.plannedStops.any { it.name.contains("과학탐구관") || it.name.contains("첨단기술관") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownInternalPointCannotBeIntroducedByAnActivity() {
        val original = CuratedPlaces.places.first().experience!!
        original.copy(undecided = original.undecided.copy(primaryPointId = "unverified_point"))
    }
}
