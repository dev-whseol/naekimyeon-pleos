package com.naekimyeon.pleos.domain

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

/** Ephemeral device fix. Never serialize it into saved UI state, analytics, or Gleo responses. */
data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val elapsedRealtimeMillis: Long,
    val isMock: Boolean = false,
    val isEmulator: Boolean = false,
)

object LocationFixPolicy {
    const val MAX_AGE_MILLIS = 120_000L
    const val MAX_ACCURACY_METERS = 5_000f

    fun isUsable(fix: LocationFix, nowElapsedMillis: Long): Boolean =
        fix.elapsedRealtimeMillis >= 0 && nowElapsedMillis >= fix.elapsedRealtimeMillis &&
            nowElapsedMillis - fix.elapsedRealtimeMillis <= MAX_AGE_MILLIS &&
            fix.accuracyMeters.isFinite() && fix.accuracyMeters > 0f &&
            fix.accuracyMeters <= MAX_ACCURACY_METERS
}

/** Straight-line proximity only. No fabricated road duration or parking entrance coordinates. */
object NearbyPlaces {
    const val DEFAULT_RADIUS_METERS = 50_000.0

    fun find(origin: GeoPoint, radiusMeters: Double = DEFAULT_RADIUS_METERS): List<Place> =
        find(CuratedPlaces.places, origin, radiusMeters)

    fun find(places: List<Place>, origin: GeoPoint, radiusMeters: Double = DEFAULT_RADIUS_METERS): List<Place> {
        require(radiusMeters.isFinite() && radiusMeters > 0 && radiusMeters <= 100_000)
        return places.asSequence().filterNot { it.provenance.isSample }.distinctBy { it.id }
            .map { it.copy(straightLineDistanceMeters = distanceMeters(origin, GeoPoint(it.latitude, it.longitude))) }
            .filter { it.straightLineDistanceMeters!! <= radiusMeters }
            .sortedWith(compareBy<Place> { it.straightLineDistanceMeters }.thenBy { it.id }).toList()
    }

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(longitudeDelta / 2).pow(2)
        return 6_371_008.8 * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
