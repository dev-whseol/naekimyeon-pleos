package com.naekimyeon.pleos.domain

enum class VisitScope { BRIEF, SINGLE_POINT, OPEN_ENDED, OPTIONAL_PAIR }

data class SceneSuggestion(
    val recommendation: Recommendation,
    val title: String,
    val activity: String,
    val rationale: String,
    val mood: MoodIntent?,
    val plannedStops: List<VisitPoint> = emptyList(),
    val visitScope: VisitScope = VisitScope.OPEN_ENDED,
    val timeNote: String = "",
) {
    val place: Place get() = recommendation.place
    val mission: String get() = activity
}

sealed class SceneResult {
    data class Ready(val scenes: List<SceneSuggestion>) : SceneResult()
    data class Empty(val reason: EmptyReason) : SceneResult()
}

/** Filters with the original travel engine first, then orders eligible experiences by the explicit mood. */
class SceneRecommender(private val engine: RecommendationEngine = RecommendationEngine()) {
    fun recommend(
        places: List<Place>, request: RecommendationRequest, mood: MoodIntent?, variation: Int = 0,
        limit: Int = 3,
    ): SceneResult {
        require(limit >= 1)
        val base = engine.recommend(places, request)
        if (base is RecommendationResult.Empty) return SceneResult.Empty(base.reason)
        val eligible = places.distinctBy { it.id }.mapNotNull { place ->
            (engine.recommend(listOf(place), request) as? RecommendationResult.Ready)?.recommendations?.singleOrNull()
        }
        val order = when (mood) {
            MoodIntent.RESET -> listOf(PlaceImage.RIVERSIDE, PlaceImage.GARDEN, PlaceImage.CAFE)
            MoodIntent.TOGETHER -> listOf(PlaceImage.GARDEN, PlaceImage.CAFE, PlaceImage.RIVERSIDE)
            MoodIntent.DISCOVERY -> listOf(PlaceImage.GARDEN, PlaceImage.RIVERSIDE, PlaceImage.CAFE)
            MoodIntent.REST -> listOf(PlaceImage.CAFE, PlaceImage.RIVERSIDE, PlaceImage.GARDEN)
            null -> emptyList()
        }
        // Nearby distance bands prevent a mood match from pushing a distant venue ahead
        // of an otherwise suitable local option. Within 5 km bands, explicit mood matters.
        val ranked = eligible.sortedWith(compareBy<Recommendation> {
            it.place.straightLineDistanceMeters?.let { meters -> (meters / 5_000).toInt() } ?: 0
        }.thenBy {
            if (mood == null) 0 else it.place.moodPreference[mood]
                ?: order.indexOf(it.place.image).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
        }.thenBy { it.place.straightLineDistanceMeters ?: Double.MAX_VALUE }
            .thenBy { it.place.outboundMinutes ?: Int.MAX_VALUE }.thenBy { it.place.id })
        val offset = if (ranked.isEmpty()) 0 else variation.coerceAtLeast(0) % ranked.size
        val rotated = ranked.drop(offset) + ranked.take(offset)
        return SceneResult.Ready(rotated.take(limit).map { scene(it, mood, request) })
    }

    private fun scene(recommendation: Recommendation, mood: MoodIntent?, request: RecommendationRequest): SceneSuggestion {
        val place = recommendation.place
        place.experience?.let { experience ->
            val plan = mood?.let(experience.byMood::get) ?: experience.undecided
            val extra = request.time == TimePreference.Unlimited && plan.extraPointId != null
            val brief = (request.time as? TimePreference.Bounded)?.minutes?.let { it <= 30 } == true
            val stopIds = listOf(plan.primaryPointId) + if (extra) listOfNotNull(plan.extraPointId) else emptyList()
            val stops = stopIds.map { id -> experience.points.single { it.id == id } }
            val scope = when {
                extra -> VisitScope.OPTIONAL_PAIR
                request.time == TimePreference.Unspecified -> VisitScope.OPEN_ENDED
                brief -> VisitScope.BRIEF
                else -> VisitScope.SINGLE_POINT
            }
            val activity = if (brief) plan.briefActivity else plan.activity
            val timeNote = when (request.time) {
                TimePreference.Unspecified -> "한 지점에서 시작하고, 더 둘러볼지는 현장에서 정하세요."
                TimePreference.Unlimited -> if (extra) "현장에서 여유가 있으면 두 지점을 이어보세요. 접근로와 출입 가능 여부는 따로 확인해 주세요."
                    else "한 곳에서 머물고, 더 둘러볼지는 그때 정하세요."
                is TimePreference.Bounded -> "이동·주차·현장 접근 시간은 따로 확인해 주세요."
            }
            return SceneSuggestion(recommendation, plan.title,
                if (extra) "$activity ${plan.extraActivity}" else activity,
                plan.rationale, mood, stops, scope, timeNote)
        }
        val copy = when (mood) {
            MoodIntent.RESET -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("강변 벤치에 앉아 하늘 보기", "벤치에 앉아 10분, 하늘만 바라보기", "잠깐 앉아서 주변을 보는 활동이에요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "음료를 마시는 동안 잠깐 휴대폰 내려놓기", "음료를 마시며 한 곳에 머물러요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 나무와 풀 찾아보기", "눈에 들어오는 나무나 풀 하나 살펴보기", "걷는 동안 가까이 있는 식물을 살펴봐요.")
            }
            MoodIntent.TOGETHER -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("풍경 사진 찍고 서로 보여주기", "서로 마음에 드는 풍경을 찍고 사진 바꿔 보기", "각자 찍은 풍경을 함께 살펴봐요.")
                PlaceImage.CAFE -> Triple("함께하고 싶은 일 세 가지 적기", "한 잔 마시며 같이 해 보고 싶은 일 세 개 적기", "같이 해 보고 싶은 일을 이야기해 보세요.")
                PlaceImage.GARDEN -> Triple("마음에 드는 나무 골라 소개하기", "각자 마음에 드는 나무를 골라 서로 소개하기", "서로 고른 나무가 어떻게 다른지 이야기해 보세요.")
            }
            MoodIntent.DISCOVERY -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("물결이나 그림자 골라 사진 찍기", "물결·그림자·색 중 하나만 골라 사진 찍기", "한 가지를 골라 자세히 살펴봐요.")
                PlaceImage.CAFE -> Triple("평소와 다른 음료 살펴보기", "메뉴에서 평소에 마시지 않던 음료 찾아보기", "주문하기 전에 메뉴부터 살펴보세요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 모양이나 색 찾아보기", "처음 눈에 들어온 모양이나 색을 사진으로 남기기", "눈에 띄는 모양이나 색을 한 가지 골라봐요.")
            }
            MoodIntent.REST -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("벤치에 앉아 강변 풍경 바라보기", "걷기보다 벤치에서 풍경 바라보기", "걷는 활동을 줄이고 한 곳에 머물러요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "카페에 앉아 음료 마시며 잠깐 쉬기", "실내 한자리에 앉아 쉬어 가요.")
                PlaceImage.GARDEN -> Triple("주변 나무 바라보며 잠시 쉬기", "눈에 들어오는 풍경 하나를 천천히 바라보기", "더 둘러보지 않고 한 곳에서 쉬어도 돼요.")
            }
            null -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("강변 벤치에 앉아 하늘 보기", "벤치에 앉아 10분, 하늘만 바라보기", "잠깐 앉았다가 더 걸을지 정해 보세요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "한 잔 마시며 잠깐 쉬어 가기", "실내에서 잠시 머무는 활동이에요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 모양이나 색 찾아보기", "마음에 드는 모양이나 색 하나 찾아보기", "가까운 곳부터 둘러보세요.")
            }
        }
        return SceneSuggestion(recommendation, copy.first, copy.second, copy.third, mood)
    }
}
