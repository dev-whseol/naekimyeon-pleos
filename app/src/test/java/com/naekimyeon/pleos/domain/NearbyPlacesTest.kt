package com.naekimyeon.pleos.domain

import org.junit.Assert.*
import org.junit.Test

class NearbyPlacesTest {
    private val seoul = GeoPoint(37.5430717, 127.0417982)
    private val fresh = LocationFix(seoul, 30f, 1_000L)

    @Test fun fixRequiresRecentMonotonicTimestampAndPositiveFiniteAccuracy() {
        assertTrue(LocationFixPolicy.isUsable(fresh, 121_000L))
        assertFalse(LocationFixPolicy.isUsable(fresh, 121_001L))
        assertFalse(LocationFixPolicy.isUsable(fresh, 999L))
        assertFalse(LocationFixPolicy.isUsable(fresh.copy(elapsedRealtimeMillis = -1), 1_000))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 5_001f).forEach {
            assertFalse(LocationFixPolicy.isUsable(fresh.copy(accuracyMeters = it), 1_000))
        }
    }

    @Test fun approximateFixRemainsUsableButRetainsItsUncertainty() {
        val approximate = fresh.copy(accuracyMeters = 4_000f)
        assertTrue(LocationFixPolicy.isUsable(approximate, 1_000))
        assertEquals(4_000f, approximate.accuracyMeters)
    }

    @Test fun mockSourceIsRetainedAndNeverRenamedAsGpsProof() {
        val injected = fresh.copy(isMock = true)
        assertTrue(LocationFixPolicy.isUsable(injected, 1_000))
        assertTrue(injected.isMock)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCoordinateIsRejected() { GeoPoint(Double.NaN, 127.0) }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeCoordinateIsRejected() { GeoPoint(91.0, 0.0) }

    @Test fun distanceUsesMetersAndWorksAcrossDateLine() {
        assertEquals(0.0, NearbyPlaces.distanceMeters(seoul, seoul), 0.0001)
        assertEquals(111_195.0, NearbyPlaces.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1.0)
        assertEquals(22_239.0, NearbyPlaces.distanceMeters(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9)), 1.0)
        assertTrue(NearbyPlaces.distanceMeters(GeoPoint(90.0, 0.0), GeoPoint(-90.0, 180.0)).isFinite())
    }

    @Test fun nearbySetChangesWithOriginAndRespectsRadius() {
        val nearForest = NearbyPlaces.find(seoul, 1_000.0)
        assertEquals(listOf("seoul_forest"), nearForest.map { it.id })
        val worldCup = CuratedPlaces.places.first { it.id == "world_cup_park" }
        val nearWest = NearbyPlaces.find(GeoPoint(worldCup.latitude, worldCup.longitude), 1_000.0)
        assertEquals(listOf("world_cup_park"), nearWest.map { it.id })
    }

    @Test fun unsupportedAreaDoesNotFallBackToSeoulSamples() {
        assertTrue(NearbyPlaces.find(GeoPoint(35.1796, 129.0756)).isEmpty())
        assertTrue(NearbyPlaces.find(DemoPlaces.places, seoul).isEmpty())
    }

    @Test fun resultDeduplicatesAndDoesNotMutateCatalogWithCurrentLocation() {
        val places = NearbyPlaces.find(CuratedPlaces.places + CuratedPlaces.places, seoul)
        assertEquals(3, places.size)
        assertEquals(places.map { it.straightLineDistanceMeters }.sortedBy { it }, places.map { it.straightLineDistanceMeters })
        assertTrue(CuratedPlaces.places.all { it.straightLineDistanceMeters == null })
    }

    @Test fun realDeadlineCandidatesNeverHaveFabricatedTravelOrStayMinutes() {
        val result = RecommendationEngine().recommend(NearbyPlaces.find(seoul), RecommendationRequest(
            time = TimePreference.Bounded(45), returnPlan = ReturnPlan.SAME_ORIGIN,
        )) as RecommendationResult.Ready
        assertTrue(result.recommendations.all {
            it.budgetStatus == BudgetStatus.TRAVEL_ESTIMATE_NEEDED && it.stayMinutes == null && it.overheadMinutes == null
        })
    }

    @Test fun unlimitedAndUnspecifiedDoNotBecomeTravelWarningsOrDeadlines() {
        val engine = RecommendationEngine()
        val places = NearbyPlaces.find(seoul)
        assertTrue((engine.recommend(places, RecommendationRequest()) as RecommendationResult.Ready)
            .recommendations.all { it.budgetStatus == BudgetStatus.NOT_SET })
        assertTrue((engine.recommend(places, RecommendationRequest(time = TimePreference.Unlimited)) as RecommendationResult.Ready)
            .recommendations.all { it.budgetStatus == BudgetStatus.NO_DEADLINE })
    }

    @Test fun indoorRequestCannotInventAnIndoorPartOfAnOutdoorPark() {
        assertEquals(RecommendationResult.Empty(EmptyReason.NO_MATCHING_NEED),
            RecommendationEngine().recommend(NearbyPlaces.find(seoul), RecommendationRequest(needs = setOf(Need.SHELTER))))
    }

    @Test fun realCatalogHasAttributionAndNoSampleDurationsOrVacancyClaims() {
        assertTrue(CuratedPlaces.places.all { !it.provenance.isSample &&
            it.provenance.sourceUrl?.startsWith("https://data.seoul.go.kr/") == true &&
            it.provenance.checkedOn == "2026-11-16" && it.address.isNotBlank() &&
            it.outboundMinutes == null && it.returnMinutes == null && it.accessMinutes == null &&
            it.parking.availability == AvailabilityState.UNKNOWN && it.visitNote.contains("대표 위치") })
    }

    @Test fun moodRankingIsNotCoupledToThreeDemoIds() {
        val places = DemoPlaces.places.map { it.copy(id = "renamed_${it.id}") }
        val engine = SceneRecommender()
        val reset = engine.recommend(places, RecommendationRequest(), MoodIntent.RESET) as SceneResult.Ready
        val rest = engine.recommend(places, RecommendationRequest(), MoodIntent.REST) as SceneResult.Ready
        assertEquals(PlaceImage.RIVERSIDE, reset.scenes.first().place.image)
        assertEquals(PlaceImage.CAFE, rest.scenes.first().place.image)
    }

    @Test fun moodCanBreakNearbyTiesButDoesNotPromoteADistantPlaceOverTheSameLocalCategory() {
        val near = CuratedPlaces.places[0].copy(straightLineDistanceMeters = 100.0)
        val far = CuratedPlaces.places[1].copy(straightLineDistanceMeters = 20_000.0)
        val engine = SceneRecommender()
        val result = engine.recommend(listOf(near, far), RecommendationRequest(), MoodIntent.DISCOVERY) as SceneResult.Ready
        assertEquals(near.id, result.scenes.first().place.id)
        val tie = engine.recommend(listOf(near, far.copy(straightLineDistanceMeters = 200.0)), RecommendationRequest(), MoodIntent.DISCOVERY) as SceneResult.Ready
        assertEquals(far.id, tie.scenes.first().place.id)
    }
}
