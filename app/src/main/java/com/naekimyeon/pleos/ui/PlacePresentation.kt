package com.naekimyeon.pleos.ui

import com.naekimyeon.pleos.domain.FacilityState
import com.naekimyeon.pleos.domain.Place
import kotlin.math.roundToInt

/** Presentation never turns straight-line distance into a route duration or parking availability. */
internal fun Place.travelLabel(): String = outboundMinutes?.let { "가는 길 약 ${it}분" } ?: "이동 시간 미확인"

internal fun Place.distanceLabel(): String? = straightLineDistanceMeters?.let {
    if (it < 1_000.0) "직선 거리 1km 미만" else "직선 거리 약 ${(it / 1_000.0).roundToInt()}km"
}

internal fun Place.parkingLabel(): String = when {
    parking.facility == FacilityState.UNKNOWN -> "주차 정보 미확인 · 빈자리 미확인"
    provenance.isSample -> "주차장 연결 예시 · 빈자리 미확인"
    else -> "주차장 정보 있음 · 빈자리 미확인"
}

internal fun Place.sourceLabel(): String = if (provenance.isSample) {
    "샘플 장소 · 예상 정보 · 실시간 정보 아님"
} else {
    "실제 장소 · 직선 거리 기준 · 실시간 정보 아님"
}
