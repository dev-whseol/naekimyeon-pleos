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

data class DataProvenance(
    val label: String, val isSample: Boolean,
    val sourceUrl: String? = null, val checkedOn: String? = null,
    val coordinateSourceUrl: String? = null,
)

/** Named on-site area verified in the operator's guide, never a vehicle navigation coordinate. */
data class VisitPoint(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val checkedOn: String,
    val accessNote: String = "현장 안내와 출입 가능 여부를 확인해 주세요.",
) {
    init { require(id.isNotBlank() && name.isNotBlank() && sourceUrl.startsWith("https://") && checkedOn.isNotBlank()) }
}

/** Editorial activities using verified areas; none of these strings establishes travel feasibility. */
data class PlaceActivity(
    val title: String,
    val primaryPointId: String,
    val activity: String,
    val rationale: String,
    val extraPointId: String? = null,
    val extraActivity: String? = null,
    val briefActivity: String = activity,
) {
    init {
        require(title.isNotBlank() && activity.isNotBlank() && rationale.isNotBlank() && briefActivity.isNotBlank())
        require((extraPointId == null) == (extraActivity == null))
        require(extraPointId == null || extraPointId != primaryPointId)
        require(extraActivity == null || extraActivity.isNotBlank())
    }
}

data class PlaceExperience(
    val points: List<VisitPoint>,
    val byMood: Map<MoodIntent, PlaceActivity>,
    val undecided: PlaceActivity,
) {
    init {
        require(points.size >= 2 && points.map { it.id }.distinct().size == points.size)
        require(byMood.keys.containsAll(MoodIntent.entries))
        val ids = points.map { it.id }.toSet()
        (byMood.values + undecided).forEach {
            require(it.primaryPointId in ids && (it.extraPointId == null || it.extraPointId in ids))
        }
    }
}

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
    val address: String = "",
    val straightLineDistanceMeters: Double? = null,
    val visitNote: String = "",
    /** Editorial preference, not measured popularity or a claim about visitors' feelings. */
    val moodPreference: Map<MoodIntent, Int> = emptyMap(),
    val experience: PlaceExperience? = null,
) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(listOfNotNull(outboundMinutes, returnMinutes, accessMinutes).all { it >= 0 })
        require(minimumStayMinutes >= 0)
        require(straightLineDistanceMeters == null || (straightLineDistanceMeters.isFinite() && straightLineDistanceMeters >= 0))
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

enum class BudgetStatus { NOT_SET, NO_DEADLINE, RETURN_PLAN_NEEDED, ESTIMATED, TRAVEL_ESTIMATE_NEEDED }

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
