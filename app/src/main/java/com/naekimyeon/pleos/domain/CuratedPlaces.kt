package com.naekimyeon.pleos.domain

/**
 * Small reviewed pilot catalog, not fabricated sample venues or a nationwide search index.
 * Coordinates and addresses: Seoul Metropolitan Government SearchParkInfoService, checked 2026-11-16.
 * These are park representative points, NOT verified vehicle/parking entrances.
 * See docs/DATA_SOURCES.md for extraction, selection, license and remaining verification.
 */
object CuratedPlaces {
    private val source = DataProvenance(
        "서울특별시 · 주요 공원현황", false,
        "https://data.seoul.go.kr/dataList/OA-394/A/1/datasetView.do", "2026-11-16",
    )
    private const val entranceNote = "공원 대표 위치예요. 주차장 입구와 차량 진입 경로는 내비에서 확인해 주세요. 운영·입장·혼잡 정보는 실시간으로 확인하지 않았어요."

    val places: List<Place> = listOf(
        park(
            "seoul_forest", "서울숲", "서울 성동구 · 도심 공원",
            "도심의 숲길을 둘러보거나 잠시 머물며 주변 나무를 바라보는 외출을 제안해요.",
            37.5430717, 127.0417982, "서울특별시 성동구 뚝섬로 273 (성수동1가)",
            mapOf(MoodIntent.RESET to 0, MoodIntent.REST to 0, MoodIntent.TOGETHER to 1, MoodIntent.DISCOVERY to 1),
            "공원 내 일부 시설의 운영시간과 출입 가능 구역은 별도로 확인해 주세요.",
        ),
        park(
            "world_cup_park", "월드컵공원", "서울 마포구 · 여러 공원으로 이어지는 산책",
            "넓은 공원에서 눈에 띄는 나무와 풍경을 찾아보는 외출을 제안해요. 공원별 진입로와 걷는 거리가 달라요.",
            37.571805, 126.878907, "서울특별시 마포구 하늘공원로 84(월드컵공원)",
            mapOf(MoodIntent.RESET to 1, MoodIntent.REST to 2, MoodIntent.TOGETHER to 2, MoodIntent.DISCOVERY to 0),
            "하늘공원 등 일부 구역은 계단이나 추가 도보 이동이 필요할 수 있어요.",
        ),
        park(
            "seoul_grand_park", "서울대공원", "경기 과천시 · 공원 산책",
            "가족이나 함께 온 사람과 공원 주변을 둘러보는 외출을 제안해요. 동물원·테마 시설 이용은 별도예요.",
            37.4264494, 127.0198465, "경기도 과천시 대공원광장로 102",
            mapOf(MoodIntent.RESET to 2, MoodIntent.REST to 1, MoodIntent.TOGETHER to 0, MoodIntent.DISCOVERY to 2),
            "동물원·테마 시설은 운영시간과 입장료를 따로 확인해 주세요. 주차 후 이동 거리가 길 수 있어요.",
        ),
    )

    private fun park(
        id: String, title: String, subtitle: String, description: String,
        latitude: Double, longitude: Double, address: String,
        moodPreference: Map<MoodIntent, Int>, note: String,
    ) = Place(
        id, title, subtitle, description, latitude, longitude,
        outboundMinutes = null, returnMinutes = null, accessMinutes = null, minimumStayMinutes = 10,
        needs = setOf(Need.BREEZE, Need.CONVERSATION), indoor = false,
        parking = ParkingInfo(FacilityState.PRESENT, "공식 자료에 주차 시설 안내가 있어요 · 빈자리·요금은 미확인", source),
        image = PlaceImage.GARDEN, provenance = source, address = address,
        visitNote = "$entranceNote\n$note", moodPreference = moodPreference,
    )
}
