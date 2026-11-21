package com.naekimyeon.pleos.domain

import org.junit.Assert.*
import org.junit.Test

class ScenePickerTest {
    private fun eligible(count: Int): List<SceneSuggestion> {
        val base = (SceneRecommender().recommend(DemoPlaces.places, RecommendationRequest(), null) as SceneResult.Ready).scenes.first()
        return (0 until count).map { base.copy(recommendation = base.recommendation.copy(place = base.place.copy(id = "place_$it"))) }
    }

    @Test fun allFiveCandidatesAppearBeforeAnyRepeat() {
        val scenes = eligible(5)
        var cycle = ScenePickCycle()
        val selected = (0 until 5).map {
            ScenePicker.next(scenes, cycle, entropy = 0)!!.also { cycle = it.cycle }.scene.place.id
        }
        assertEquals(scenes.map { it.place.id }.toSet(), selected.toSet())
        assertEquals(5, selected.size)
    }

    @Test fun nextCycleCannotBeginWithTheLastPlaceFromPreviousCycle() {
        val scenes = eligible(5)
        var cycle = ScenePickCycle()
        repeat(5) { cycle = ScenePicker.next(scenes, cycle, Int.MIN_VALUE)!!.cycle }
        val next = ScenePicker.next(scenes, cycle, Int.MIN_VALUE)!!
        assertTrue(next.startsNewCycle)
        assertNotEquals(cycle.lastPlaceId, next.scene.place.id)
        assertEquals(4, next.remainingInCycle)
    }

    @Test fun changedEligibilityCannotReturnRemovedPlacesAndRetainsSeenIntersection() {
        val scenes = eligible(5)
        val old = ScenePickCycle(setOf("place_0", "place_1", "removed"), "place_1")
        val reduced = listOf(scenes[1], scenes[3])
        val next = ScenePicker.next(reduced, old, 0)!!
        assertEquals("place_3", next.scene.place.id)
        assertEquals(setOf("place_1", "place_3"), next.cycle.seenPlaceIds)
    }

    @Test fun newlyEligibleCandidateIsSeenBeforeStartingAnotherCycle() {
        val scenes = eligible(3)
        val cycle = ScenePickCycle(setOf("place_0", "place_1"), "place_1")
        val next = ScenePicker.next(scenes, cycle, 0)!!
        assertEquals("place_2", next.scene.place.id)
        assertFalse(next.startsNewCycle)
    }

    @Test fun singleCandidateAndEmptySetAreExplicit() {
        val one = eligible(1)
        val first = ScenePicker.next(one, ScenePickCycle(), Int.MAX_VALUE)!!
        val second = ScenePicker.next(one, first.cycle, -1)!!
        assertEquals(first.scene.place.id, second.scene.place.id)
        assertTrue(second.startsNewCycle)
        assertNull(ScenePicker.next(emptyList(), second.cycle, 0))
    }

    @Test fun duplicateIdsNeverAddAnotherTurnToTheCycle() {
        val scenes = eligible(3)
        var cycle = ScenePickCycle()
        repeat(3) { cycle = ScenePicker.next(scenes + scenes, cycle, 0)!!.cycle }
        assertEquals(3, cycle.seenPlaceIds.size)
        assertTrue(ScenePicker.next(scenes + scenes, cycle, 0)!!.startsNewCycle)
    }

    @Test fun boundedNearbyFirstPickUsesClosestTwoButLaterPicksStillCoverAll() {
        val scenes = eligible(5).mapIndexed { index, item ->
            item.copy(recommendation = item.recommendation.copy(place = item.place.copy(straightLineDistanceMeters = (index + 1) * 1_000.0)))
        }.reversed()
        repeat(10) { seed ->
            val first = ScenePicker.next(scenes, ScenePickCycle(), seed, nearFirst = true)!!
            assertTrue(first.scene.place.id in setOf("place_0", "place_1"))
            assertTrue(first.usedNearFirst)
            var cycle = first.cycle
            repeat(4) {
                val next = ScenePicker.next(scenes, cycle, seed, nearFirst = true)!!
                assertFalse(next.usedNearFirst)
                cycle = next.cycle
            }
            assertEquals(5, cycle.seenPlaceIds.size)
        }
    }
}
