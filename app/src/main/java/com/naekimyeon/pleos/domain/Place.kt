package com.naekimyeon.pleos.domain

/** A preference is optional. Unknown time and explicitly having no deadline are distinct. */
sealed class TimePreference {
    data object Unspecified : TimePreference()
    data object Unlimited : TimePreference()
    data class Bounded(val minutes: Int) : TimePreference() {
        init { require(minutes in 1..240) }
    }
}

enum class Need { BREEZE, CONVERSATION, SHELTER }
enum class ReturnPlan { UNSPECIFIED, SAME_ORIGIN }
enum class PlaceImage { RIVERSIDE, CAFE, GARDEN }
enum class FacilityState { UNKNOWN, PRESENT }
enum class AvailabilityState { UNKNOWN }

/** A platform request contains explicit user choices only; a new id makes repeated requests observable. */
data class IncomingRequest(
    val id: Long, val need: Need? = null, val minutes: Int? = null,
    val unlimited: Boolean = false, val feeling: String? = null,
)

data class DataProvenance(val label: String, val isSample: Boolean)

data class ParkingInfo(
    val facility: FacilityState,
    val note: String,
    val provenance: DataProvenance,
    // A known facility never implies an available space.
    val availability: AvailabilityState = AvailabilityState.UNKNOWN,
)

data class Place(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val outboundMinutes: Int?,
    val returnMinutes: Int?,
    val accessMinutes: Int?,
    val minimumStayMinutes: Int,
    val needs: Set<Need>,
    val indoor: Boolean,
    val parking: ParkingInfo,
    val image: PlaceImage,
    val provenance: DataProvenance,
) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(listOfNotNull(outboundMinutes, returnMinutes, accessMinutes).all { it >= 0 })
        require(minimumStayMinutes >= 0)
    }
}

data class RecommendationRequest(
    val needs: Set<Need> = emptySet(),
    val time: TimePreference = TimePreference.Unspecified,
    val returnPlan: ReturnPlan = ReturnPlan.UNSPECIFIED,
    val safetyMinutes: Int = 5,
) {
    init { require(safetyMinutes >= 0) }
}

enum class BudgetStatus { NOT_SET, NO_DEADLINE, RETURN_PLAN_NEEDED, ESTIMATED }

data class Recommendation(
    val place: Place,
    val budgetStatus: BudgetStatus,
    val stayMinutes: Int? = null,
    val overheadMinutes: Int? = null,
)

enum class EmptyReason { NO_PLACES, NO_MATCHING_NEED, NOT_ENOUGH_TIME, MISSING_TRAVEL_ESTIMATES }

sealed class RecommendationResult {
    data class Ready(val recommendations: List<Recommendation>) : RecommendationResult()
    data class Empty(val reason: EmptyReason) : RecommendationResult()
}
