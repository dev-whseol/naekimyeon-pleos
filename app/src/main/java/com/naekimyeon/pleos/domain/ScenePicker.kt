package com.naekimyeon.pleos.domain

/** A deterministic draw from already eligible scenes. It cannot relax constraints or navigate. */
object ScenePicker {
    fun pick(eligible: List<SceneSuggestion>, previousPlaceId: String?, entropy: Int): SceneSuggestion? {
        val unique = eligible.distinctBy { it.place.id }
        if (unique.isEmpty()) return null
        val pool = if (unique.size > 1) unique.filterNot { it.place.id == previousPlaceId } else unique
        return pool[Math.floorMod(entropy, pool.size)]
    }
}
