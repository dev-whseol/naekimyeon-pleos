package com.naekimyeon.pleos.domain

/** Deterministic and offline. No location, traffic, weather or parking availability is inferred. */
class RecommendationEngine {
    fun recommend(places: List<Place>, request: RecommendationRequest): RecommendationResult {
        if (places.isEmpty()) return RecommendationResult.Empty(EmptyReason.NO_PLACES)
        val matching = places.distinctBy { it.id }.filter { place ->
            request.needs.isEmpty() || request.needs.all { it in place.needs }
        }.sortedWith(compareBy<Place> { it.outboundMinutes ?: Int.MAX_VALUE }
            .thenBy { it.straightLineDistanceMeters ?: Double.MAX_VALUE }.thenBy { it.id })
        if (matching.isEmpty()) return RecommendationResult.Empty(EmptyReason.NO_MATCHING_NEED)

        val bounded = request.time as? TimePreference.Bounded
        val canEstimate = bounded != null && request.returnPlan == ReturnPlan.SAME_ORIGIN
        var missingEstimates = false
        val items = matching.mapNotNull { place ->
            when {
                request.time == TimePreference.Unlimited -> Recommendation(place, BudgetStatus.NO_DEADLINE)
                request.time == TimePreference.Unspecified -> Recommendation(place, BudgetStatus.NOT_SET)
                // Real places can be browsed without pretending they fit the user's deadline.
                // Demo fixture behavior remains unchanged for the submitted 0.2 flows.
                !place.provenance.isSample &&
                    (place.outboundMinutes == null || place.returnMinutes == null || place.accessMinutes == null) ->
                    Recommendation(place, BudgetStatus.TRAVEL_ESTIMATE_NEEDED)
                !canEstimate -> Recommendation(place, BudgetStatus.RETURN_PLAN_NEEDED)
                else -> {
                    val outbound = place.outboundMinutes
                    val back = place.returnMinutes
                    val access = place.accessMinutes
                    if (outbound == null || back == null || access == null) {
                        missingEstimates = true
                        null
                    } else {
                        val overhead = outbound.toLong() + back.toLong() + access.toLong() + request.safetyMinutes.toLong()
                        val stay = bounded!!.minutes.toLong() - overhead
                        if (stay < place.minimumStayMinutes) null
                        else Recommendation(place, BudgetStatus.ESTIMATED, stay.toInt(), overhead.toInt())
                    }
                }
            }
        }.take(3)
        if (items.isEmpty()) return RecommendationResult.Empty(
            if (missingEstimates) EmptyReason.MISSING_TRAVEL_ESTIMATES else EmptyReason.NOT_ENOUGH_TIME
        )
        return RecommendationResult.Ready(items)
    }
}
