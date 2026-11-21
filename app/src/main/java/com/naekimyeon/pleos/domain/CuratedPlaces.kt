package com.naekimyeon.pleos.domain

/**
 * Small reviewed pilot catalog, not fabricated sample venues or a nationwide search index.
 * Park coordinates: Seoul SearchParkInfoService (2026-11-16).
 * Indoor representative points: operator-linked map / Korea Tourism Organization (2026-11-21).
 * These are venue representative points, NOT verified vehicle/parking entrances or internal stop points.
 * See docs/DATA_SOURCES.md for extraction, selection, license and remaining verification.
 */
object CuratedPlaces {
    private val source = DataProvenance(
        "서울특별시 · 주요 공원현황", false,
        "https://data.seoul.go.kr/dataList/OA-394/A/1/datasetView.do", "2026-11-16",
    )
    private const val entranceNote = "장소의 대표 위치예요. 주차장 입구와 차량 진입 경로는 내비에서 확인해 주세요. 운영·입장·혼잡 정보는 실시간으로 확인하지 않았어요."
    private val historySource = DataProvenance(
        "서울역사박물관 · 관람·전시 안내", false,
        "https://museum.seoul.go.kr/chd/watch/watchGuideMap.jsp?tr_code=sweb", "2026-11-21",
        "https://maps.app.goo.gl/n362YJAy8Z5461qX6",
    )
    private val scienceSource = DataProvenance(
        "국립과천과학관 · 한국관광공사", false,
        "https://access.visitkorea.or.kr/ms/detail.do?cotId=d1f83bef-8fe6-4b8e-9c9f-36e1b2b6d20f", "2026-11-21",
        "https://access.visitkorea.or.kr/ms/detail.do?cotId=d1f83bef-8fe6-4b8e-9c9f-36e1b2b6d20f",
    )

    val places: List<Place> = listOf(
        park(
            "seoul_forest", "서울숲", "서울 성동구 · 도심 공원",
            "거울연못과 가족마당, 군마상을 골라 둘러볼 수 있는 공원이에요.",
            37.5430717, 127.0417982, "서울특별시 성동구 뚝섬로 273 (성수동1가)",
            mapOf(MoodIntent.RESET to 0, MoodIntent.REST to 0, MoodIntent.TOGETHER to 1, MoodIntent.DISCOVERY to 1),
            "공원 내 일부 시설의 운영시간과 출입 가능 구역은 별도로 확인해 주세요.",
            CuratedExperiences.seoulForest,
        ),
        park(
            "world_cup_park", "월드컵공원", "서울 마포구 · 여러 공원으로 이어지는 산책",
            "난지연못, 평화잔디광장, 메타세콰이어길에서 할 일을 골라보세요. 각 구역의 접근로와 걷는 거리는 달라요.",
            37.571805, 126.878907, "서울특별시 마포구 하늘공원로 84(월드컵공원)",
            mapOf(MoodIntent.RESET to 1, MoodIntent.REST to 2, MoodIntent.TOGETHER to 2, MoodIntent.DISCOVERY to 0),
            "하늘공원 등 일부 구역은 계단이나 추가 도보 이동이 필요할 수 있어요.",
            CuratedExperiences.worldCupPark,
        ),
        park(
            "seoul_grand_park", "서울대공원", "경기 과천시 · 공원 산책",
            "청계저수지 호숫가와 둑방길에서 한 구간을 골라보세요. 동물원·테마 시설 이용은 별도예요.",
            37.4264494, 127.0198465, "경기도 과천시 대공원광장로 102",
            mapOf(MoodIntent.RESET to 2, MoodIntent.REST to 1, MoodIntent.TOGETHER to 0, MoodIntent.DISCOVERY to 2),
            "동물원·테마 시설은 운영시간과 입장료를 따로 확인해 주세요. 주차 후 이동 거리가 길 수 있어요.",
            CuratedExperiences.seoulGrandPark,
        ),
        Place(
            id = "seoul_history_museum", title = "서울역사박물관", subtitle = "서울 종로구 · 실내 전시",
            description = "서울 모형에서 아는 동네를 찾거나, 서울이 변해 온 모습을 보여주는 전시를 골라볼 수 있어요.",
            latitude = 37.5704616, longitude = 126.9705126,
            outboundMinutes = null, returnMinutes = null, accessMinutes = null, minimumStayMinutes = 0,
            needs = setOf(Need.SHELTER, Need.CONVERSATION), indoor = true,
            parking = ParkingInfo(FacilityState.UNKNOWN, "주차 시설과 이용 조건은 별도로 확인해 주세요 · 빈자리 미확인", historySource),
            image = PlaceImage.GARDEN, provenance = historySource,
            address = "서울특별시 종로구 새문안로 55",
            visitNote = "$entranceNote\n실내 전시 관람 후보예요. 휴관·전시 교체·입장 가능 여부는 운영기관 안내를 확인해 주세요.",
            moodPreference = mapOf(MoodIntent.RESET to 1, MoodIntent.REST to 0, MoodIntent.TOGETHER to 1, MoodIntent.DISCOVERY to 0),
            experience = CuratedExperiences.seoulHistoryMuseum,
        ),
        Place(
            id = "gwacheon_science_museum", title = "국립과천과학관", subtitle = "경기 과천시 · 실내 과학 전시",
            description = "자연사관과 한국과학문명관에서 지구나 옛 측정 도구를 다룬 전시를 골라볼 수 있어요.",
            latitude = 37.438119, longitude = 127.005777,
            outboundMinutes = null, returnMinutes = null, accessMinutes = null, minimumStayMinutes = 0,
            needs = setOf(Need.SHELTER, Need.CONVERSATION), indoor = true,
            parking = ParkingInfo(FacilityState.PRESENT, "공식 자료에 주차 시설 안내가 있어요 · 빈자리·요금·진입점 미확인", scienceSource),
            image = PlaceImage.GARDEN, provenance = scienceSource,
            address = "경기도 과천시 상하벌로 110",
            visitNote = "$entranceNote\n이번 활동은 자연사관·한국과학문명관만 다뤄요. 공사·개관 일정이 바뀌는 다른 전시관과 예약형 체험은 포함하지 않아요. 입장권·전시실 개방 여부를 먼저 확인해 주세요.",
            moodPreference = mapOf(MoodIntent.RESET to 2, MoodIntent.REST to 2, MoodIntent.TOGETHER to 0, MoodIntent.DISCOVERY to 0),
            experience = CuratedExperiences.gwacheonScienceMuseum,
        ),
    )

    private fun park(
        id: String, title: String, subtitle: String, description: String,
        latitude: Double, longitude: Double, address: String,
        moodPreference: Map<MoodIntent, Int>, note: String, experience: PlaceExperience,
    ) = Place(
        id, title, subtitle, description, latitude, longitude,
        outboundMinutes = null, returnMinutes = null, accessMinutes = null, minimumStayMinutes = 10,
        needs = setOf(Need.BREEZE, Need.CONVERSATION), indoor = false,
        parking = ParkingInfo(FacilityState.PRESENT, "공식 자료에 주차 시설 안내가 있어요 · 빈자리·요금은 미확인", source),
        image = PlaceImage.GARDEN, provenance = source, address = address,
        visitNote = "$entranceNote\n$note", moodPreference = moodPreference,
        experience = experience,
    )
}
