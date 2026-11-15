package com.naekimyeon.pleos.domain

/** Illustrative fixtures, never a source of current traffic or business information. */
object DemoPlaces {
    val source = DataProvenance("샘플 장소 · 예상 정보", isSample = true)
    val places = listOf(
        Place(
            id = "riverside", title = "강변 산책길", subtitle = "바람 따라, 가볍게 한 바퀴",
            description = "강을 따라 천천히 걷거나 벤치에서 쉬는 모습을 그려 보세요. 특별한 계획 없이 바깥 공기를 느끼고 싶은 순간에 어울려요.",
            latitude = 37.5293, longitude = 127.0699,
            outboundMinutes = 12, returnMinutes = 14, accessMinutes = 4, minimumStayMinutes = 10,
            needs = setOf(Need.BREEZE, Need.CONVERSATION), indoor = false,
            parking = ParkingInfo(FacilityState.PRESENT, "공영주차장 연결 예시", source),
            image = PlaceImage.RIVERSIDE, provenance = source,
        ),
        Place(
            id = "window_cafe", title = "창가의 작은 카페", subtitle = "따뜻한 한 잔, 나란히 앉아서",
            description = "창밖을 보며 이야기를 나눌 수 있는 아늑한 실내 공간 예시예요. 비를 피하거나 잠시 쉬고 싶은 마음에 맞춰 골랐어요.",
            latitude = 37.5445, longitude = 127.0422,
            outboundMinutes = 9, returnMinutes = 11, accessMinutes = 6, minimumStayMinutes = 10,
            needs = setOf(Need.CONVERSATION, Need.SHELTER), indoor = true,
            parking = ParkingInfo(FacilityState.UNKNOWN, "주차 정보가 아직 없어요", source),
            image = PlaceImage.CAFE, provenance = source,
        ),
        Place(
            id = "forest_rest", title = "나무 그늘 쉼터", subtitle = "초록 가까이, 잠깐 쉬어 가요",
            description = "나무 사이로 걷고 조용히 쉬는 공원 공간을 제안해요. 여유가 조금 더 있을 때 느긋하게 머무는 장면을 담았어요.",
            latitude = 37.5446, longitude = 127.0375,
            outboundMinutes = 18, returnMinutes = 20, accessMinutes = 6, minimumStayMinutes = 10,
            needs = setOf(Need.BREEZE, Need.CONVERSATION), indoor = false,
            parking = ParkingInfo(FacilityState.PRESENT, "공원 주차장 연결 예시", source),
            image = PlaceImage.GARDEN, provenance = source,
        ),
    )
}
