package com.naekimyeon.pleos.domain

data class ScenePickCycle(
    val seenPlaceIds: Set<String> = emptySet(),
    val lastPlaceId: String? = null,
    val completedCycles: Int = 0,
)

data class ScenePick(
    val scene: SceneSuggestion,
    val cycle: ScenePickCycle,
    val startsNewCycle: Boolean,
    val remainingInCycle: Int,
    val usedNearFirst: Boolean = false,
)

/** Deterministic selection over the complete eligible set; travel conditions are never relaxed here. */
object ScenePicker {
    fun next(eligible: List<SceneSuggestion>, cycle: ScenePickCycle, entropy: Int, nearFirst: Boolean = false): ScenePick? {
        val unique = eligible.distinctBy { it.place.id }
        if (unique.isEmpty()) return null
        val ids = unique.map { it.place.id }.toSet()
        val stillSeen = cycle.seenPlaceIds.intersect(ids)
        val unseen = unique.filterNot { it.place.id in stillSeen }
        val restarts = unseen.isEmpty()
        val useNear = nearFirst && !restarts && stillSeen.isEmpty() && unique.size >= 3 &&
            unique.count { it.place.straightLineDistanceMeters != null } >= 2
        val pool = if (useNear) unique.sortedBy { it.place.straightLineDistanceMeters ?: Double.MAX_VALUE }.take(2)
            else if (restarts && unique.size > 1) unique.filterNot { it.place.id == cycle.lastPlaceId }
            else if (restarts) unique else unseen
        val chosen = pool[Math.floorMod(entropy, pool.size)]
        val newSeen = (if (restarts) emptySet() else stillSeen) + chosen.place.id
        return ScenePick(
            scene = chosen,
            cycle = ScenePickCycle(newSeen, chosen.place.id, cycle.completedCycles + if (restarts) 1 else 0),
            startsNewCycle = restarts,
            remainingInCycle = unique.size - newSeen.size,
            usedNearFirst = useNear,
        )
    }

    /** Compatibility for callers that only remember the immediately previous place. */
    fun pick(eligible: List<SceneSuggestion>, previousPlaceId: String?, entropy: Int): SceneSuggestion? {
        return next(eligible, ScenePickCycle(setOfNotNull(previousPlaceId), previousPlaceId), entropy)?.scene
    }
}
