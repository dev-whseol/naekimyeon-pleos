package com.naekimyeon.pleos.domain

data class SceneSuggestion(
    val recommendation: Recommendation,
    val title: String,
    val activity: String,
    val rationale: String,
    val mood: MoodIntent?,
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
    ): SceneResult {
        val base = engine.recommend(places, request)
        if (base is RecommendationResult.Empty) return SceneResult.Empty(base.reason)
        val eligible = places.distinctBy { it.id }.mapNotNull { place ->
            (engine.recommend(listOf(place), request) as? RecommendationResult.Ready)?.recommendations?.singleOrNull()
        }
        val order = when (mood) {
            MoodIntent.RESET -> listOf("riverside", "forest_rest", "window_cafe")
            MoodIntent.TOGETHER -> listOf("forest_rest", "window_cafe", "riverside")
            MoodIntent.DISCOVERY -> listOf("forest_rest", "riverside", "window_cafe")
            MoodIntent.REST -> listOf("window_cafe", "riverside", "forest_rest")
            null -> emptyList()
        }
        val ranked = eligible.sortedWith(compareBy<Recommendation> {
            order.indexOf(it.place.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
        }.thenBy { it.place.outboundMinutes ?: Int.MAX_VALUE }.thenBy { it.place.id })
        val offset = if (ranked.isEmpty()) 0 else variation.coerceAtLeast(0) % ranked.size
        val rotated = ranked.drop(offset) + ranked.take(offset)
        return SceneResult.Ready(rotated.take(3).map { scene(it, mood) })
    }

    private fun scene(recommendation: Recommendation, mood: MoodIntent?): SceneSuggestion {
        val place = recommendation.place
        val copy = when (mood) {
            MoodIntent.RESET -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("강변 벤치에 앉아 하늘 보기", "벤치에 앉아 10분, 하늘만 바라보기", "잠시 머리를 식히며 가볍게 머물기 좋은 곳이에요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "따뜻한 음료를 두고 잠깐 휴대폰 뒤집어 두기", "이동 후 실내 한자리에서 편히 쉬기 좋은 곳이에요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 나무와 풀 찾아보기", "눈에 들어오는 초록 세 가지 찾아보기", "생각에서 잠시 눈앞의 풍경으로 시선을 옮기는 제안이에요.")
            }
            MoodIntent.TOGETHER -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("풍경 사진 찍고 서로 보여주기", "서로 마음에 드는 풍경을 찍고 사진 바꿔 보기", "함께 보낸 시간을 작은 사진 한 장으로 남기는 장면이에요.")
                PlaceImage.CAFE -> Triple("함께하고 싶은 일 세 가지 적기", "한 잔 마시며 같이 해 보고 싶은 일 세 개 적기", "익숙한 대화에 작은 놀이를 더해 둘만의 기억을 만들어 봐요.")
                PlaceImage.GARDEN -> Triple("마음에 드는 나무 골라 소개하기", "각자 마음에 드는 나무를 골라 서로 소개하기", "둘이 새로운 추억을 만들고 싶은 마음에, 함께 할 작은 활동을 먼저 골랐어요.")
            }
            MoodIntent.DISCOVERY -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("물결이나 그림자 골라 사진 찍기", "물결·그림자·색 중 하나만 골라 사진 찍기", "새로움은 멀리 가지 않아도, 보는 방식을 바꾸면 시작될 수 있어요.")
                PlaceImage.CAFE -> Triple("오늘 마실 음료에 이름 붙이기", "마시고 싶은 음료를 고르고 오늘 기분을 이름으로 붙이기", "평범한 카페 시간을 나만의 작은 경험으로 바꾸는 제안이에요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 모양이나 색 찾아보기", "처음 눈에 들어온 모양이나 색을 사진으로 남기기", "평소와 다른 풍경을 둘러보며 가볍게 사진 남기기 좋은 곳이에요.")
            }
            MoodIntent.REST -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("벤치에 앉아 강변 풍경 바라보기", "걷기보다 벤치에서 풍경 바라보기", "편하게 쉬고 싶은 마음에, 이동 뒤 활동을 서두르지 않는 제안이에요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "따뜻한 한 잔과 함께 오늘의 속도 내려놓기", "쉬고 싶은 마음에 맞춰, 실내에 앉아 보내는 장면을 먼저 골랐어요.")
                PlaceImage.GARDEN -> Triple("주변 나무 바라보며 잠시 쉬기", "눈에 들어오는 풍경 하나를 천천히 바라보기", "무언가를 해내기보다 쉬는 시간을 남겨 두는 장면이에요.")
            }
            null -> when (place.image) {
                PlaceImage.RIVERSIDE -> Triple("강변 벤치에 앉아 하늘 보기", "벤치에 앉아 10분, 하늘만 바라보기", "아직 마음을 정하지 않았다면, 가볍게 시작할 수 있는 장면이에요.")
                PlaceImage.CAFE -> Triple("카페에 앉아 음료 마시며 쉬기", "따뜻한 한 잔과 함께 잠깐 쉬어 가기", "정해 둔 마음 없이도 살펴볼 수 있는 실내 장면이에요.")
                PlaceImage.GARDEN -> Triple("눈에 띄는 모양이나 색 찾아보기", "마음에 드는 모양이나 색 하나 찾아보기", "선택을 열어 둔 채, 새로운 풍경을 만나는 장면이에요.")
            }
        }
        return SceneSuggestion(recommendation, copy.first, copy.second, copy.third, mood)
    }
}
