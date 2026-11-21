package com.naekimyeon.pleos.ui

import com.naekimyeon.pleos.domain.MoodIntent
import com.naekimyeon.pleos.domain.Place
import com.naekimyeon.pleos.domain.RecommendationRequest

internal enum class HomeAction { ONE, COMPARE }

/** Transient explicit intent. Raw text and coordinates are never saved or reinterpreted on resume. */
internal data class HomeRecommendationIntent(
    val id: Long,
    val action: HomeAction,
    val sampleMode: Boolean,
    val queryRevision: Long,
    val request: RecommendationRequest,
    val mood: MoodIntent?,
    val startedAtMillis: Long,
    val day: Long,
    val catalog: List<Place>,
    val waitsForLocation: Boolean,
    val initialLocationBasis: Any?,
    val initialLocationStatus: Any?,
) {
    fun isCurrent(revision: Long, sample: Boolean, restricted: Boolean, nowMillis: Long, currentDay: Long, currentCatalog: List<Place>) =
        !restricted && revision == queryRevision && sample == sampleMode && day == currentDay &&
            nowMillis >= startedAtMillis && nowMillis - startedAtMillis <= 60_000 && catalog == currentCatalog
}
