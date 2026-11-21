package com.naekimyeon.pleos.domain

/** Named areas are source-backed; activities are editorial choices, not guaranteed timed itineraries. */
internal object CuratedExperiences {
    private const val checked = "2026-11-21"
    private const val forest = "https://parks.seoul.go.kr/park/detailView.do?pIdx=5"
    private const val worldCup = "https://parks.seoul.go.kr/park/detailView.do?pIdx=6"
    private const val grandPark = "https://grandpark.seoul.go.kr/conts/contsView/ko/S001004002003001.do"
    private const val cityModel = "https://museum.seoul.go.kr/www/exh/per/exhPer5th.jsp?sso=ok"
    private const val capitalGallery = "https://museum.seoul.go.kr/www/exh/per/exhPer4th.jsp?sso=ok"
    private const val naturalHistory = "https://smart.sciencecenter.go.kr/gcsci/perm/exh_view.php?cat=C&idx=101079"
    private const val koreanScience = "https://www.sciencecenter.go.kr/gnsm/exhibit/civil"

    private fun point(id: String, name: String, source: String, note: String) =
        VisitPoint(id, name, source, checked, note)

    private fun activity(
        title: String, point: String, brief: String, standard: String, reason: String,
        extraPoint: String? = null, extra: String? = null,
    ) = PlaceActivity(title, point, standard, reason, extraPoint, extra, brief)

    val seoulForest = PlaceExperience(
        points = listOf(
            point("mirror_pond", "거울연못", forest, "겨울철에는 수경시설을 운영하지 않을 수 있어요. 물에 비친 풍경을 보장하지 않아요."),
            point("family_lawn", "가족마당", forest, "잔디 보호나 행사로 이용 구역이 달라질 수 있어요."),
            point("horse_statue", "군마상", forest, "조형물과 관람객 사이의 거리를 두고 둘러보세요."),
        ),
        byMood = mapOf(
            MoodIntent.RESET to activity("거울연못 앞에서 나무 풍경 보기", "mirror_pond",
                "거울연못 앞에서 눈에 들어오는 나무 한 그루 바라보기",
                "거울연못 가장자리에서 나무 풍경을 보며 잠깐 머물기",
                "거울연못 주변 나무를 보는 데 집중해요. 물이 없는 시기에도 주변을 살펴볼 수 있어요.",
                "family_lawn", "더 둘러보고 싶으면 가족마당으로 이어가 보세요."),
            MoodIntent.TOGETHER to activity("가족마당 둘레를 걸으며 이야기하기", "family_lawn",
                "가족마당 가까운 구간에서 함께 온 사람과 이야기하기",
                "가족마당 둘레를 걷고, 마음에 드는 곳에서 이야기를 이어가기",
                "가족마당은 넓은 잔디광장이에요. 이용 가능한 구역 안에서 함께 둘러보세요.",
                "horse_statue", "여유가 있으면 군마상도 함께 살펴보세요."),
            MoodIntent.DISCOVERY to activity("군마상에서 말의 자세 살펴보기", "horse_statue",
                "군마상에서 말 한 마리의 자세만 살펴보기",
                "군마상을 앞과 옆에서 보며 말의 자세 비교하기",
                "서울숲이 경마장으로 쓰이던 때를 기념한 조형물이에요.",
                "mirror_pond", "다음에는 거울연못 주변의 풍경도 둘러보세요."),
            MoodIntent.REST to activity("가족마당 가까운 곳에서 잠깐 쉬기", "family_lawn",
                "가족마당 가까운 곳에서 쉴 자리부터 확인하기",
                "가족마당 주변에 쉴 자리가 있으면 잠깐 머물기",
                "공원을 한 바퀴 돌지 않고 한 곳에 머물러요. 쉴 자리의 이용 가능 여부는 현장에서 확인해 주세요."),
        ),
        undecided = activity("거울연못부터 가볍게 둘러보기", "mirror_pond",
            "거울연못 주변에서 눈에 들어오는 풍경 하나 보기",
            "거울연못 주변을 보고 더 둘러볼지는 그때 정하기",
            "처음부터 공원 전체를 돌 필요는 없어요.",
            "family_lawn", "더 걷고 싶으면 가족마당으로 이어가 보세요."),
    )

    val worldCupPark = PlaceExperience(
        points = listOf(
            point("nanji_pond", "난지연못", worldCup, "분수 가동과 데크·평상 이용 가능 여부는 현장에서 확인해 주세요."),
            point("peace_lawn", "평화잔디광장", worldCup, "행사와 잔디 보호 구역을 확인해 주세요."),
            point("metasequoia_path", "메타세콰이어길", worldCup, "난지연못과는 다른 구역이에요. 현장 지도에서 접근로를 확인해 주세요."),
        ),
        byMood = mapOf(
            MoodIntent.RESET to activity("난지연못가 짧은 구간 걷기", "nanji_pond",
                "난지연못가에서 가까운 구간만 걷기",
                "난지연못가를 걷다가 물가 풍경을 보며 잠깐 머물기",
                "평화의공원 안의 연못가를 둘러봐요. 분수가 켜져 있다고 가정하지 않아요.",
                "peace_lawn", "더 걷고 싶으면 평화잔디광장도 둘러보세요."),
            MoodIntent.TOGETHER to activity("평화잔디광장 둘레에서 이야기하기", "peace_lawn",
                "평화잔디광장 가까운 구간에서 함께 이야기하기",
                "평화잔디광장 둘레를 걸으며 서로 눈에 들어오는 풍경 이야기하기",
                "광장 한 곳에서 함께 시간을 보내요. 행사 중인 구역은 피해 주세요.",
                "nanji_pond", "여유가 있으면 난지연못가로 이어가 보세요."),
            MoodIntent.DISCOVERY to activity("메타세콰이어길에서 나무 줄기 살펴보기", "metasequoia_path",
                "메타세콰이어길에서 가까운 나무 한 그루 살펴보기",
                "메타세콰이어길의 한 구간을 걸으며 나무 줄기와 길의 모양 비교하기",
                "길 전체를 걷는 일정은 아니에요. 들어온 길과 돌아갈 길을 먼저 확인해 주세요.",
                "nanji_pond", "더 둘러보고 싶으면 접근로를 확인한 뒤 난지연못을 찾아가 보세요."),
            MoodIntent.REST to activity("자리가 있으면 난지연못 데크에서 쉬기", "nanji_pond",
                "난지연못 데크에서 앉을 자리가 있는지 확인하기",
                "자리가 있으면 난지연못 데크에 머물며 풍경 보기",
                "한 곳에서 쉬는 선택이에요. 데크·평상의 빈자리와 이용 가능 여부는 확인하지 않았어요."),
        ),
        undecided = activity("난지연못가부터 둘러보기", "nanji_pond",
            "난지연못가 가까운 구간부터 둘러보기",
            "난지연못가를 보고 더 걸을지는 그때 정하기",
            "여러 공원을 한꺼번에 돌지 않고 평화의공원 안에서 시작해요.",
            "peace_lawn", "더 걷고 싶으면 평화잔디광장으로 이어가 보세요."),
    )

    val seoulGrandPark = PlaceExperience(
        points = listOf(
            point("lake_walk", "청계저수지 호숫가", grandPark, "전체 둘레길은 공식 안내상 2.8km예요. 여기서는 한 구간만 골라 걸어요."),
            point("lake_dam", "청계저수지 둑방길", grandPark, "날씨와 현장 출입 안내를 확인해 주세요."),
            point("lake_tables", "호숫가 야외탁자", grandPark, "호숫가 산책로의 야외탁자예요. 빈자리나 그늘을 보장하지 않아요."),
        ),
        byMood = mapOf(
            MoodIntent.RESET to activity("청계저수지 호숫가 한 구간 걷기", "lake_walk",
                "청계저수지 호숫가에서 가까운 구간만 걷기",
                "청계저수지 호숫가 한 구간을 걷고 돌아갈 지점 정하기",
                "호수를 한 바퀴 도는 일정이 아니에요. 전체 둘레길과 이동 시간을 구분해 주세요.",
                "lake_dam", "더 걷고 싶으면 현장 안내를 확인하고 둑방길로 이어가 보세요."),
            MoodIntent.TOGETHER to activity("둑방길에서 호수 풍경 함께 보기", "lake_dam",
                "청계저수지 둑방길 한 곳에서 호수 풍경 함께 보기",
                "청계저수지 둑방길에서 함께 보이는 풍경을 이야기하며 머물기",
                "같이 온 사람의 걷는 속도에 맞춰 한 구간만 둘러봐요.",
                "lake_walk", "여유가 있으면 호숫가의 다른 구간도 함께 걸어보세요."),
            MoodIntent.DISCOVERY to activity("둑방길에서 호수와 산 쪽 풍경 비교하기", "lake_dam",
                "둑방길에서 호수 쪽과 산 쪽을 한 번씩 바라보기",
                "둑방길 한 구간에서 방향을 바꿔 호수와 산 쪽 풍경 비교하기",
                "날씨에 따라 보이는 풍경이 달라요. 멀리까지 선명하게 보인다고 가정하지 않아요.",
                "lake_walk", "더 둘러보고 싶으면 호숫가 쪽에서도 풍경을 보세요."),
            MoodIntent.REST to activity("자리가 있으면 호숫가 야외탁자에서 쉬기", "lake_tables",
                "호숫가 야외탁자에 쉴 자리가 있는지 확인하기",
                "자리가 있으면 호숫가 야외탁자에 앉아 잠깐 쉬기",
                "둘레길을 더 걷기보다 한 곳에 머물러요. 주차 후 탁자까지의 거리는 따로 확인해 주세요."),
        ),
        undecided = activity("청계저수지 호숫가부터 둘러보기", "lake_walk",
            "청계저수지 호숫가에서 가까운 풍경 하나 보기",
            "청계저수지 호숫가 한 구간을 보고 더 걸을지는 그때 정하기",
            "동물원이나 테마 시설에 들어가는 일정은 포함하지 않아요.",
            "lake_dam", "여유가 있으면 둑방길도 둘러보세요."),
    )

    val seoulHistoryMuseum = PlaceExperience(
        points = listOf(
            point("city_model", "도시모형영상관", cityModel, "본관 3층의 서울 모형 전시 공간이에요. 전시실 개방 여부를 확인해 주세요."),
            point("capital_gallery", "상설 4존 · 대한민국 수도 서울", capitalGallery, "본관 3층 전시실이에요. 전시 교체와 출입 안내를 확인해 주세요."),
        ),
        byMood = mapOf(
            MoodIntent.RESET to activity("도시모형영상관에서 한 동네만 골라 보기", "city_model",
                "도시모형영상관에서 아는 동네 하나 찾기",
                "도시모형영상관에서 동네 하나를 골라 주변 모양까지 살펴보기",
                "서울 전체를 축소한 모형에서 한 부분만 골라봐요.",
                "capital_gallery", "더 보고 싶으면 상설 4존에서 서울이 변한 모습도 살펴보세요."),
            MoodIntent.TOGETHER to activity("서울 모형에서 서로 아는 동네 찾아보기", "city_model",
                "서울 모형에서 서로 아는 동네 하나씩 찾기",
                "도시모형영상관에서 서로 아는 동네를 찾아 작은 목소리로 이야기하기",
                "같은 모형을 보며 각자 기억하는 장소를 찾아봐요. 다른 관람객을 배려해 주세요.",
                "capital_gallery", "여유가 있으면 상설 4존도 함께 둘러보세요."),
            MoodIntent.DISCOVERY to activity("상설 4존에서 서울이 변한 모습 비교하기", "capital_gallery",
                "상설 4존에서 눈에 들어오는 자료 하나 보기",
                "상설 4존에서 서울이 달라진 모습을 보여주는 자료 비교하기",
                "해방 이후 서울의 변화와 생활상을 다루는 전시실이에요.",
                "city_model", "그다음 도시모형영상관에서 서울의 전체 모습도 살펴보세요."),
            MoodIntent.REST to activity("상설 4존에서 관심 가는 전시물 하나만 보기", "capital_gallery",
                "상설 4존에서 관심 가는 전시물 하나 고르기",
                "상설 4존에서 고른 전시물 하나를 천천히 보기",
                "모든 전시실을 둘러보지 않아도 돼요. 실내 관람이며 앉을 자리는 확인하지 않았어요."),
        ),
        undecided = activity("서울 모형에서 아는 동네 찾아보기", "city_model",
            "도시모형영상관에서 익숙한 동네 하나 찾기",
            "도시모형영상관에서 시작하고 다른 전시실을 볼지는 그때 정하기",
            "전시실 운영과 입장 가능 여부는 방문 전에 확인해 주세요.",
            "capital_gallery", "더 보고 싶으면 상설 4존으로 이어가 보세요."),
    )

    val gwacheonScienceMuseum = PlaceExperience(
        points = listOf(
            point("natural_history", "자연사관", naturalHistory, "전시실 개방과 전시물 가동 여부를 확인해 주세요. 별도 체험 프로그램은 포함하지 않아요."),
            point("korean_science", "한국과학문명관", koreanScience, "전시 교체와 관람 안내를 확인해 주세요. 체험 장치의 이용 가능 여부는 미확인이에요."),
        ),
        byMood = mapOf(
            MoodIntent.RESET to activity("자연사관에서 지구 모습 살펴보기", "natural_history",
                "SOS 전시 안내에서 지구의 한 부분 보기",
                "자연사관 SOS 전시 안내에서 지구의 하늘과 바다 모습 살펴보기",
                "여러 전시관을 돌지 않고 지구를 다룬 전시 한 곳에서 시작해요.",
                "korean_science", "더 보고 싶으면 한국과학문명관도 둘러보세요."),
            MoodIntent.TOGETHER to activity("한국과학문명관에서 측우기 알아보기", "korean_science",
                "측우기 설명에서 비를 재는 방법 하나 읽기",
                "측우기 설명을 보고 함께 비를 재는 방법 이야기하기",
                "같이 온 사람과 전시 설명을 읽어봐요. 다른 관람객을 배려해 작은 목소리로 이야기해 주세요.",
                "natural_history", "여유가 있으면 자연사관에서도 궁금한 전시를 함께 골라보세요."),
            MoodIntent.DISCOVERY to activity("한국과학문명관에서 옛 측정 도구 비교하기", "korean_science",
                "한국과학문명관에서 측우기 모양 살펴보기",
                "한국과학문명관의 측우기와 도량형 전시에서 쓰임새 비교하기",
                "옛사람들이 비와 물건의 크기를 어떻게 쟀는지 전시 설명에서 찾아봐요.",
                "natural_history", "더 둘러보고 싶으면 자연사관의 지구 전시로 이어가 보세요."),
            MoodIntent.REST to activity("자연사관에서 관심 가는 전시 하나만 보기", "natural_history",
                "자연사관에서 관심 가는 전시물 하나 고르기",
                "자연사관에서 고른 전시물 하나만 천천히 살펴보기",
                "전시관 전체를 둘러보지 않아도 돼요. 앉을 자리와 관람 동선은 현장에서 확인해 주세요."),
        ),
        undecided = activity("자연사관에서 궁금한 전시 골라보기", "natural_history",
            "자연사관에서 눈에 들어오는 전시물 하나 보기",
            "자연사관에서 시작하고 다른 전시관을 볼지는 그때 정하기",
            "실내 상설전시부터 둘러봐요. 운영과 입장 가능 여부는 방문 전에 확인해 주세요.",
            "korean_science", "더 보고 싶으면 한국과학문명관으로 이어가 보세요."),
    )
}
