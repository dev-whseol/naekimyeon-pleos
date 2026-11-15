package com.naekimyeon.pleos.domain

import org.junit.Assert.*
import org.junit.Test

class MoodSceneTest {
    private val interpreter = MoodInterpreter()
    private val recommender = SceneRecommender()
    private fun scenes(mood: MoodIntent?, request: RecommendationRequest = RecommendationRequest(), variation: Int = 0) =
        (recommender.recommend(DemoPlaces.places, request, mood, variation) as SceneResult.Ready).scenes

    @Test fun allQuickPhrasesMapToTheirExplicitIntent() {
        MoodIntent.entries.forEach { assertEquals(MoodInterpretation.Matched(it), interpreter.interpret(it.phrase)) }
    }
    @Test fun unknownEmptyAndOverlongInputAreNotInventedIntents() {
        assertEquals(MoodInterpretation.Empty, interpreter.interpret("  "))
        assertEquals(MoodInterpretation.Unknown, interpreter.interpret("파란 코끼리"))
        assertEquals(MoodInterpretation.Unknown, interpreter.interpret("쉬".repeat(121)))
    }
    @Test fun negativeAndAvailabilityPhrasesAreNotPretendedToBeKnown() {
        listOf("쉬고 싶지 않아", "걷기 싫어", "사람 없는 곳", "주차가 가능한 곳").forEach {
            assertEquals(it, MoodInterpretation.Unknown, interpreter.interpret(it))
        }
    }
    @Test fun moodActuallyChangesRankingNotJustTheHeading() {
        val orders = MoodIntent.entries.map { mood -> scenes(mood).map { it.place.id } }
        assertEquals(4, orders.toSet().size)
        assertEquals("riverside", scenes(MoodIntent.RESET).first().place.id)
        assertEquals("window_cafe", scenes(MoodIntent.REST).first().place.id)
        assertEquals("forest_rest", scenes(MoodIntent.DISCOVERY).first().place.id)
    }
    @Test fun samePlaceBecomesADifferentConcreteActivityForDifferentMoods() {
        val reset = scenes(MoodIntent.RESET).first { it.place.id == "riverside" }
        val together = scenes(MoodIntent.TOGETHER).first { it.place.id == "riverside" }
        assertNotEquals(reset.title, together.title)
        assertNotEquals(reset.activity, together.activity)
        assertEquals(reset.place, together.place)
    }
    @Test fun moodsAndVariationCannotBypassExplicitTimeAndNeedConstraints() {
        val request = RecommendationRequest(setOf(Need.BREEZE), TimePreference.Bounded(45), ReturnPlan.SAME_ORIGIN)
        MoodIntent.entries.forEach { mood ->
            val item = scenes(mood, request, variation = 5).single()
            assertEquals("riverside", item.place.id)
            assertEquals(10, item.recommendation.stayMinutes)
            assertEquals(35, item.recommendation.overheadMinutes)
        }
    }
    @Test fun unknownTimeAndNoDeadlineStayDistinctAcrossMoods() {
        assertTrue(scenes(MoodIntent.REST).all { it.recommendation.budgetStatus == BudgetStatus.NOT_SET })
        assertTrue(scenes(MoodIntent.REST, RecommendationRequest(time = TimePreference.Unlimited))
            .all { it.recommendation.budgetStatus == BudgetStatus.NO_DEADLINE })
    }
    @Test fun boundedTimeDoesNotInferAReturnPlanFromAFeeling() {
        assertTrue(scenes(MoodIntent.RESET, RecommendationRequest(time = TimePreference.Bounded(1)))
            .all { it.recommendation.budgetStatus == BudgetStatus.RETURN_PLAN_NEEDED && it.recommendation.stayMinutes == null })
    }
    @Test fun playfulVariationReordersWithoutAddingOrDuplicatingPlaces() {
        val first = scenes(MoodIntent.DISCOVERY)
        val next = scenes(MoodIntent.DISCOVERY, variation = 1)
        assertNotEquals(first.map { it.place.id }, next.map { it.place.id })
        assertEquals(first.map { it.place.id }.toSet(), next.map { it.place.id }.toSet())
        assertTrue(next.size <= 3)
        assertTrue(next.all { it.place.parking.availability == AvailabilityState.UNKNOWN && it.place.provenance.isSample })
    }
    @Test fun impossibleTripRemainsEmptyForEveryMood() {
        val request = RecommendationRequest(time = TimePreference.Bounded(30), returnPlan = ReturnPlan.SAME_ORIGIN)
        MoodIntent.entries.forEach {
            assertEquals(SceneResult.Empty(EmptyReason.NOT_ENOUGH_TIME), recommender.recommend(DemoPlaces.places, request, it))
        }
    }
    @Test fun drawNeverEscapesTheEligibleSetAndHandlesEmpty() {
        val eligible = scenes(MoodIntent.RESET, RecommendationRequest(setOf(Need.BREEZE), TimePreference.Bounded(45), ReturnPlan.SAME_ORIGIN))
        listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE).forEach {
            assertEquals("riverside", ScenePicker.pick(eligible, null, it)?.place?.id)
        }
        assertNull(ScenePicker.pick(emptyList(), null, 0))
    }
    @Test fun drawAvoidsImmediateRepeatWhenThereIsAnotherEligibleScene() {
        val eligible = scenes(MoodIntent.DISCOVERY)
        eligible.forEach { previous ->
            repeat(20) { assertNotEquals(previous.place.id, ScenePicker.pick(eligible, previous.place.id, it)?.place?.id) }
        }
        assertEquals(eligible.first(), ScenePicker.pick(listOf(eligible.first()), eligible.first().place.id, 0))
    }
}
